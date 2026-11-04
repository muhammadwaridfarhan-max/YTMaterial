package com.example.ytclient.data

import com.example.ytclient.auth.AuthManager
import com.example.ytclient.auth.DeviceCodeAuth
import com.example.ytclient.auth.GoogleUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

/** Something that hands out pages of videos, page after page. */
interface Pager {
    val exhausted: Boolean
    suspend fun nextBatch(): List<VideoItem>
}

/**
 * Feed of the signed-in YouTube account (the same home / subscriptions feeds as the YouTube TV
 * app), read with the OAuth token from the device-code login. If the account feed cannot be
 * loaded the first time, [fallback] takes over so the screen never ends up empty.
 */
class AccountPager(
    private val browseId: String,
    private val fallback: (() -> Pager)? = null
) : Pager {
    private var delegate: Pager? = null
    private var token: String? = null
    private var started = false
    private var loadedAny = false
    private var ownExhausted = false
    private val seen = HashSet<String>()

    override val exhausted: Boolean get() = delegate?.exhausted ?: ownExhausted

    override suspend fun nextBatch(): List<VideoItem> {
        delegate?.let { return it.nextBatch() }
        return try {
            fetchNext()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            val fb = fallback
            if (!loadedAny && fb != null) {
                val p = fb()
                delegate = p
                p.nextBatch()
            } else {
                throw e
            }
        }
    }

    private suspend fun fetchNext(): List<VideoItem> = withContext(Dispatchers.IO) {
        var attempts = 0
        while (!ownExhausted && attempts++ < 6) {
            val json = if (!started) {
                YtAccountApi.browse(browseId)
            } else {
                val t = token
                if (t == null) {
                    ownExhausted = true
                    break
                }
                YtAccountApi.continuation(t)
            }
            started = true
            token = YtAccountApi.nextToken(json)
            if (token == null) ownExhausted = true
            val fresh = YtAccountApi.parseVideos(json).filter { seen.add(it.url) }
            if (fresh.isNotEmpty()) {
                loadedAny = true
                return@withContext fresh
            }
        }
        if (!loadedAny) throw IllegalStateException("Feed akun kosong")
        emptyList()
    }

    companion object {
        const val HOME = "default"
        const val SUBSCRIPTIONS = "FEsubscriptions"
    }
}

/**
 * Authenticated InnerTube calls (TVHTML5 client). With a bearer token the request carries no API
 * key, only `Authorization: Bearer ...` - exactly how MeeTube/SmartTube talk to YouTube.
 *
 * NOTE: this is YouTube's unofficial TV API. It can change without notice.
 */
object YtAccountApi {
    private const val BROWSE = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val ACCOUNTS =
        "https://www.youtube.com/youtubei/v1/account/accounts_list?prettyPrint=false"
    private const val CLIENT_VERSION = "7.20260707.07.00"
    private val JSON = "application/json".toMediaType()

    private fun body(extra: JSONObject): JSONObject {
        val loc = Locale.getDefault()
        val client = JSONObject()
            .put("clientName", "TVHTML5")
            .put("clientVersion", CLIENT_VERSION)
            .put("clientScreen", "WATCH")
            .put("userAgent", DeviceCodeAuth.UA)
            .put(
                "tvAppInfo",
                JSONObject().put("appQuality", "TV_APP_QUALITY_FULL_ANIMATION")
                    .put("zylonLeftNav", true)
            )
            .put("webpSupport", false)
            .put("animatedWebpSupport", true)
            .put("acceptLanguage", loc.toLanguageTag())
            .put("acceptRegion", loc.country.ifBlank { "US" })
            .put("utcOffsetMinutes", (TimeZone.getDefault().rawOffset / 60000).toString())
            .put("visitorData", DeviceCodeAuth.visitorData().orEmpty())
        val root = JSONObject()
            .put(
                "context",
                JSONObject().put("client", client).put(
                    "user",
                    JSONObject().put("enableSafetyMode", false).put("lockedSafetyMode", false)
                )
            )
            .put("racyCheckOk", true)
            .put("contentCheckOk", true)
        val keys = extra.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            root.put(k, extra.get(k))
        }
        return root
    }

    private fun call(url: String, json: JSONObject, token: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", DeviceCodeAuth.UA)
            .header("Referer", "https://www.youtube.com/tv")
            .apply { DeviceCodeAuth.visitorData()?.let { header("X-Goog-Visitor-Id", it) } }
            .post(json.toString().toRequestBody(JSON))
            .build()
        DeviceCodeAuth.client.newCall(req).execute().use { r ->
            return r.code to r.body?.string().orEmpty()
        }
    }

    private suspend fun authedPost(url: String, json: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            // Makes sure the client data (and visitor id) are loaded before the first call.
            runCatching { DeviceCodeAuth.clientData() }
            val first = AuthManager.accessToken() ?: throw IOException("Belum login")
            var (code, text) = call(url, json, first)
            if (code == 401 || code == 403) {
                val fresh = AuthManager.forceRefresh() ?: throw IOException("Sesi login habis")
                val retry = call(url, json, fresh)
                code = retry.first
                text = retry.second
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            JSONObject(text)
        }

    suspend fun browse(browseId: String): JSONObject =
        authedPost(BROWSE, body(JSONObject().put("browseId", browseId)))

    suspend fun continuation(token: String): JSONObject =
        authedPost(BROWSE, body(JSONObject().put("continuation", token)))

    /** Name, handle and photo of the signed-in Google/YouTube account. */
    suspend fun profile(): GoogleUser? {
        val json = authedPost(
            ACCOUNTS,
            body(
                JSONObject().put(
                    "accountReadMask",
                    JSONObject().put("returnOwner", true).put("returnBrandAccounts", true)
                        .put("returnPersonaAccounts", false)
                )
            )
        )
        val items = ArrayList<JSONObject>()
        collect(json, "accountItem", items)
        val item = items.firstOrNull { it.optBoolean("isSelected") } ?: items.firstOrNull()
            ?: return null
        val name = text(item.optJSONObject("accountName")).orEmpty()
        val handle = text(item.optJSONObject("channelHandle"))
            ?: text(item.optJSONObject("accountByline")).orEmpty()
        val photo = item.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
            ?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .maxByOrNull { it.optInt("width", 0) }?.optString("url")
            }?.let { if (it.startsWith("//")) "https:$it" else it }
        if (name.isBlank() && handle.isBlank()) return null
        return GoogleUser(name.ifBlank { handle }, handle, photo)
    }

    // ---- parsing -------------------------------------------------------------------------

    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val names = node.keys()
                while (names.hasNext()) {
                    val k = names.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) out.add(v) else collect(v, key, out)
                }
            }

            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    private fun text(o: JSONObject?): String? {
        if (o == null) return null
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return o.optString("content").ifBlank { null }
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            .ifBlank { null }
    }

    private fun parseDuration(s: String): Long {
        val parts = s.trim().split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty() || parts.size > 3) return 0
        return parts.fold(0L) { acc, v -> acc * 60 + v }
    }

    fun parseVideos(json: JSONObject): List<VideoItem> {
        val tiles = ArrayList<JSONObject>()
        collect(json, "tileRenderer", tiles)
        return tiles.mapNotNull { tileToVideo(it) }
    }

    private fun tileToVideo(tile: JSONObject): VideoItem? {
        val type = tile.optString("contentType")
        if (type.isNotEmpty() && !type.contains("VIDEO") && !type.contains("SHORTS")) return null
        val id = tile.optJSONObject("onSelectCommand")?.optJSONObject("watchEndpoint")
            ?.optString("videoId").orEmpty().ifBlank { tile.optString("contentId") }
        if (id.isBlank()) return null

        val meta = tile.optJSONObject("metadata")?.optJSONObject("tileMetadataRenderer")
        val title = text(meta?.optJSONObject("title")).orEmpty()
        if (title.isBlank()) return null

        val texts = ArrayList<String>()
        val lines = meta?.optJSONArray("lines")
        if (lines != null) {
            for (i in 0 until lines.length()) {
                val items = lines.optJSONObject(i)?.optJSONObject("lineRenderer")
                    ?.optJSONArray("items") ?: continue
                for (j in 0 until items.length()) {
                    text(items.optJSONObject(j)?.optJSONObject("lineItemRenderer")?.optJSONObject("text"))
                        ?.let { texts.add(it) }
                }
            }
        }
        val channel = texts.firstOrNull().orEmpty()
        val rest = texts.drop(1)
        val viewsText = rest.firstOrNull { t ->
            val l = t.lowercase()
            listOf("view", "ditonton", "tonton", "vues", "aufrufe", "visualiza").any { l.contains(it) }
        } ?: rest.firstOrNull()
        val uploaded = rest.firstOrNull { it != viewsText }

        val header = tile.optJSONObject("header")?.optJSONObject("tileHeaderRenderer")
        val thumbs = header?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = thumbs?.let { arr ->
            val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            (list.filter { it.optInt("width") >= 400 }.minByOrNull { it.optInt("width") }
                ?: list.maxByOrNull { it.optInt("width") })?.optString("url")
        }?.let { if (it.startsWith("//")) "https:$it" else it }

        var duration = 0L
        var live = false
        var short = type.contains("SHORTS")
        val overlays = header?.optJSONArray("thumbnailOverlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val o = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                    ?: continue
                val style = o.optString("style")
                val t = text(o.optJSONObject("text")).orEmpty()
                if (style == "LIVE") live = true
                if (style == "SHORTS") short = true
                if (duration == 0L) duration = parseDuration(t)
            }
        }

        return VideoItem(
            title = title,
            url = "https://www.youtube.com/watch?v=$id",
            channel = channel,
            channelAvatar = ChannelCache.avatar(channel),
            thumbnail = thumb,
            durationSec = duration,
            views = -1,
            uploaded = uploaded,
            isLive = live,
            channelUrl = ChannelCache.url(channel),
            viewsText = viewsText,
            isShort = short
        )
    }

    private fun path(root: Any?, vararg p: Any): Any? {
        var cur: Any? = root
        for (k in p) {
            cur = when {
                k is String && cur is JSONObject -> cur.opt(k)
                k is Int && cur is JSONArray -> cur.opt(k)
                else -> return null
            }
        }
        return cur
    }

    /** Token for the next page of the feed (section list / grid level), or null at the end. */
    fun nextToken(json: JSONObject): String? {
        val surface = arrayOf("contents", "tvBrowseRenderer", "content", "tvSurfaceContentRenderer")
        val candidates = listOf(
            arrayOf(*surface, "content", "sectionListRenderer", "continuations", 0, "nextContinuationData", "continuation"),
            arrayOf(*surface, "content", "gridRenderer", "continuations", 0, "nextContinuationData", "continuation"),
            arrayOf(*surface, "continuation", "reloadContinuationData", "continuation"),
            arrayOf("continuationContents", "sectionListContinuation", "continuations", 0, "nextContinuationData", "continuation"),
            arrayOf("continuationContents", "gridContinuation", "continuations", 0, "nextContinuationData", "continuation"),
            arrayOf("continuationContents", "horizontalListContinuation", "continuations", 0, "nextContinuationData", "continuation")
        )
        for (c in candidates) {
            (path(json, *c) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
