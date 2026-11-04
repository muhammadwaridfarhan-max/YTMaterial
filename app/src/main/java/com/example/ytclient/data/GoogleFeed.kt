package com.example.ytclient.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Minimal YouTube Data API v3 feed synced from the signed-in Google account.

 * Requires the OAuth scope [https://www.googleapis.com/auth/youtube] (read-only)
 * and uses the stored access token from [AccountManager].
 *
 * Feeds implemented:
 *   - subscriptions (channels joined for `mine=true` + their upload playlist)
 *   - liked videos (the "Liked videos" playlist, id "LV")
 *   - your recent uploads / watched videos (search `mine=true`)
 *
 * The video [url] is always the canonical `https://www.youtube.com/watch?v=...`; the
 * metadata is only informational (title, channel, avatar, thumbnail, duration, views).
 */
object GoogleFeed {

    private const val API_BASE = "https://www.googleapis.com/youtube/v3/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Turn the stored base64url token into a utf-8 string. */
    private fun decodeToken(token: String): String = withContext(Dispatchers.IO) {
        val padded = token.replace(Regex("_-"), "/").replace(Regex("-"), "+")
        val sep = if (padded.length % 4 == 0) "" else "====".substring(0, 4 - padded.length % 4)
        val bytes = Base64.decode(padded + sep, Base64.URL_SAFE or Base64.NO_WRAP)
        String(bytes, StandardCharsets.UTF_8)
    }

    /** Build an @Request that carries the Bearer access token. */
    private fun Request.Builder.tokenAuth(token: String, path: String, params: List<Pair<String, String>>): Request {
        val q = params.joinToString("&") { (k, v) ->
            "${k}=${java.net.URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
        return this.url(API_BASE + path + "?" + q).header("Authorization", "Bearer $token").build()
    }

    /** Resolve a token once and release it for the whole batch of calls. */
    private fun tokenHandle(token: String): String = withContext(Dispatchers.IO) {
        val raw = decodeToken(token)
        if (raw.isBlank()) throw IOException("empty_token")
        raw
    }

    /** Parse one raw API item into a [VideoItem]. */
    private fun toVideoItem(v: JSONObject): VideoItem? = withContext(Dispatchers.IO) {
        val s = v.optJSONObject("snippet") ?: return@withContext null
        val title = s.optString("title")
        val vid = s.optJSONArray("resourceId")?.optString("videoId") ?: return@withContext null
        val thumb = s.optJSONArray("thumbnails").optJSONObject("default")?.optString("url")
        val views = s.optString("viewCount")
        val channelTitle = s.optString("channelTitle")
        val publishTime = s.optString("publishTime")
        VideoItem(
            title = title,
            url = "https://www.youtube.com/watch?v=$vid",
            channel = channelTitle,
            channelAvatar = thumb,
            thumbnail = thumb,
            durationSec = 0L,
            views = views.toLongOrNull() ?: 0L,
            uploaded = publishTime,
            isLive = false
        )
    }

    /** Pull the raw array of items from an API endpoint. */
    private fun readItems(token: String, path: String, params: List<Pair<String, String>>): List<JSONObject> = withContext(Dispatchers.IO) {
        val raw = tokenHandle(token)
        val req = Request.Builder().apply { tokenAuth(raw, path, params) }.build()
        http.newCall(req).execute().use { resp ->
            if (resp.code != 200) {
                val body = resp.body?.string().orEmpty()
                if (resp.code == 401) throw IOException("token_expired")
                throw IOException("HTTP ${resp.code} $body")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("items") ?: return@withContext emptyList()
            items.toList().mapNotNull { it as? JSONObject }
        }
    }

    /** Home/Cloud feed: every subscription channel + its upload playlist, newest first. */
    suspend fun subscriptions(token: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val raw = tokenHandle(token)
        val out = mutableListOf<VideoItem>()
        // 1. Channels forMe=true (contentDetails gives the upload playlist id).
        val channelParams = listOf("part" to "snippet,contentDetails,statistics", "mine" to "true", "maxResults" to "50")
        for (ch in readItems(raw, "channels", channelParams)) {
            val title = ch.optJSONObject("snippet")?.optString("title")
            val thumb = ch.optJSONObject("snippet")?.optJSONArray("thumbnails")?.optJSONObject("default")?.optString("url")
            val plUrl = ch.optJSONObject("contentDetails")?.optString("relatedPlaylists.upload")
            if (title.isBlank() || plUrl.isBlank()) continue
            // 2. That playlist's videos.
            val page = listOf("part" to "snippet", "playlistId" to plUrl, "maxResults" to "25")
            for (v in readItems(raw, "playlistItems", page)) {
                val item = toVideoItem(v)?.copy(channel = title, channelAvatar = thumb)
                if (item != null) out += item
            }
        }
        out
    }

    /** "Liked videos" playlist. */
    suspend fun likedVideos(token: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val raw = tokenHandle(token)
        readItems(raw, "playlistItems", listOf("part" to "snippet", "playlistId" to "LV", "maxResults" to "25"))
            .mapNotNull { toVideoItem(it) }
    }

    /** Your uploads / watched videos via search(mine=true). */
    suspend fun recent(token: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val raw = tokenHandle(token)
        readItems(raw, "search", listOf("part" to "snippet", "mine" to "true", "type" to "video", "maxResults" to "25"))
            .mapNotNull { toVideoItem(it) }
    }

    /** Endless pager over Google feeds. Same page-by-page semantics as [FeedPager] but the
     * sources are suspend lambdas returning [VideoItem] (not NewPipe extractors). */
    class GoogleFeedPager(
        private val sources: List<suspend (String) -> List<VideoItem>>,
        private val accept: (VideoItem) -> Boolean = { true }
    ) {
        private var index = -1
        private var loadedAny = false
        private val seen = HashSet<String>()

        @Volatile
        var exhausted = false
            private set

        suspend fun nextBatch(accessToken: String): List<VideoItem> = withContext(Dispatchers.IO) {
            var attempts = 0
            while (!exhausted && attempts++ < 8) {
                index++
                if (index >= sources.size) {
                    exhausted = true
                    break
                }
                val fresh = runCatching {
                    sources[index].invoke(accessToken)
                }.getOrDefault(emptyList())
                val filtered = fresh.filter { accept(it) && it.url != null && seen.add(it.url) }
                if (filtered.isNotEmpty()) {
                    loadedAny = true
                    return@withContext filtered
                }
            }
            if (!loadedAny && !exhausted) throw IllegalStateException("Tidak ada video Google ditemukan")
            emptyList()
        }
    }
}
