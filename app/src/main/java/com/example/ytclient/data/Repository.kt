package com.example.ytclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.Immutable
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class VideoItem(
    val title: String,
    val url: String,
    val channel: String,
    val channelAvatar: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val isLive: Boolean,
    val channelUrl: String? = null,
    /** Ready-made views text (account feed gives "1,2 jt x ditonton" instead of a number). */
    val viewsText: String? = null,
    val verified: Boolean = false,
    val isShort: Boolean = false
)

/** Channel name -> avatar / url, filled while parsing feeds so cards and channel pages reuse them. */
object ChannelCache {
    private val avatars = ConcurrentHashMap<String, String>()
    private val urls = ConcurrentHashMap<String, String>()
    private fun key(name: String) = name.trim().lowercase()

    fun put(name: String, avatar: String?, url: String?) {
        if (name.isBlank()) return
        if (!avatar.isNullOrBlank()) avatars[key(name)] = avatar
        if (!url.isNullOrBlank()) urls[key(name)] = url
    }

    fun avatar(name: String): String? = if (name.isBlank()) null else avatars[key(name)]
    fun url(name: String): String? = if (name.isBlank()) null else urls[key(name)]
}

enum class SearchFilter { ALL, SHORTS, LIVE, RECENT }

@Immutable
data class ChannelDetails(
    val name: String,
    val url: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,
    val description: String,
    val verified: Boolean
)

class ChannelBundle(val details: ChannelDetails, val tabs: Map<String, ListLinkHandler>)

data class StreamTrack(
    val height: Int,
    val fps: Int,
    val url: String,
    val ext: String = "mp4",
    val codec: String = ""
)

data class VideoStreams(
    val isLive: Boolean,
    val hlsUrl: String?,
    val audioUrl: String?,
    val videoOnly: List<StreamTrack>,
    val progressive: List<StreamTrack>,
    val audioExt: String = "m4a"
) {
    val heights: List<Int>
        get() = (videoOnly.map { it.height } + progressive.map { it.height })
            .distinct().sortedDescending()
}

/**
 * Endless feed: walks page by page through one source (kiosk / search) and, when that source
 * runs dry, moves on to the next one so the list never hits a dead end.
 */
class FeedPager(
    private val sources: List<() -> ListExtractor<out InfoItem>>,
    private val accept: (StreamInfoItem) -> Boolean = { true }
) : Pager {
    private var index = -1
    private var current: ListExtractor<out InfoItem>? = null
    private var next: Page? = null
    private var loadedAny = false
    private val seen = HashSet<String>()

    @Volatile
    override var exhausted = false
        private set

    /** Next batch of new videos; empty only when every source is used up. */
    override suspend fun nextBatch(): List<VideoItem> = withContext(Dispatchers.IO) {
        var attempts = 0
        while (!exhausted && attempts++ < 8) {
            val raw = try {
                fetch()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                current = null
                next = null
                if (!loadedAny && index >= sources.size - 1) throw e
                emptyList()
            }
            val fresh = raw.filter { accept(it) && it.url != null && seen.add(it.url) }
            if (fresh.isNotEmpty()) {
                loadedAny = true
                return@withContext fresh.map { it.toVideoItem() }
            }
        }
        if (!loadedAny && !exhausted) throw IllegalStateException("Tidak ada video ditemukan")
        emptyList()
    }

    private fun fetch(): List<StreamInfoItem> {
        val ext = current
        val cursor = next
        val page: ListExtractor.InfoItemsPage<out InfoItem>
        if (ext == null || cursor == null) {
            index++
            if (index >= sources.size) {
                exhausted = true
                return emptyList()
            }
            val e = sources[index]()
            current = e
            e.fetchPage()
            page = e.initialPage
        } else {
            page = ext.getPage(cursor)
        }
        next = if (page.hasNextPage()) page.nextPage else null
        return page.items.filterIsInstance<StreamInfoItem>()
    }
}

internal fun StreamInfoItem.toVideoItem(
    fallbackChannel: String? = null,
    fallbackAvatar: String? = null
): VideoItem {
    val avatar = runCatching {
        (uploaderAvatars.filter { it.height >= 48 }.minByOrNull { it.height }
            ?: uploaderAvatars.maxByOrNull { it.height })?.url
    }.getOrNull() ?: fallbackAvatar
    val channelName = (uploaderName ?: "").ifBlank { fallbackChannel.orEmpty() }
    ChannelCache.put(channelName, avatar, uploaderUrl)
    return VideoItem(
        title = name ?: "",
        url = url ?: "",
        channel = channelName,
        channelAvatar = avatar,
        // Mid-size thumbnail (>= 270px tall) instead of the biggest one: far lighter to decode while scrolling.
        thumbnail = (thumbnails.filter { it.height >= 270 }.minByOrNull { it.height }
            ?: thumbnails.maxByOrNull { it.height })?.url,
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        isLive = streamType == StreamType.LIVE_STREAM,
        channelUrl = uploaderUrl,
        verified = isUploaderVerified,
        isShort = isShortFormContent || (url ?: "").contains("/shorts/")
    )
}

/** One tab (Videos / Shorts / Live) of a channel, page after page. */
class ChannelTabPager(
    private val handler: ListLinkHandler,
    private val channelName: String,
    private val channelAvatar: String?
) : Pager {
    private var next: Page? = null
    private var started = false
    private val seen = HashSet<String>()

    @Volatile
    override var exhausted = false
        private set

    override suspend fun nextBatch(): List<VideoItem> = withContext(Dispatchers.IO) {
        var attempts = 0
        while (!exhausted && attempts++ < 4) {
            val items: List<InfoItem>
            if (!started) {
                val info = ChannelTabInfo.getInfo(ServiceList.YouTube, handler)
                started = true
                items = info.relatedItems
                next = info.nextPage
            } else {
                val n = next
                if (n == null) {
                    exhausted = true
                    break
                }
                val page = ChannelTabInfo.getMoreItems(ServiceList.YouTube, handler, n)
                items = page.items
                next = page.nextPage
            }
            if (next == null) exhausted = true
            val fresh = items.filterIsInstance<StreamInfoItem>()
                .filter { it.url != null && seen.add(it.url) }
                .map { it.toVideoItem(channelName, channelAvatar) }
            if (fresh.isNotEmpty()) return@withContext fresh
        }
        emptyList()
    }
}

object Repository {
    private val service get() = ServiceList.YouTube

    private val SHORT_QUERIES = listOf(
        "#shorts", "shorts viral", "shorts lucu", "shorts musik", "shorts gaming",
        "shorts kuliner", "shorts hewan lucu", "shorts olahraga", "shorts tutorial", "shorts motivasi"
    )

    private val HOME_EXTRA = listOf(
        "musik populer", "trending indonesia", "viral hari ini", "berita terbaru", "film pendek",
        "game seru", "tutorial", "olahraga", "kuliner", "teknologi terbaru"
    )

    private fun searchExtractor(query: String): ListExtractor<out InfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        return service.getSearchExtractor(handler)
    }

    /** Home feed: trending kiosk first, then topic searches, page after page. */
    fun homePager(): FeedPager = FeedPager(
        listOf<() -> ListExtractor<out InfoItem>>({ service.kioskList.defaultKioskExtractor }) +
            HOME_EXTRA.map { q -> { searchExtractor(q) } }
    )

    /** Endless results for a query (also used by the home filter chips). */
    fun searchPager(query: String, filter: SearchFilter = SearchFilter.ALL): FeedPager {
        val queries = when (filter) {
            SearchFilter.ALL -> listOf(query, "$query populer", "$query terbaru", "$query hari ini", "best $query")
            SearchFilter.SHORTS -> listOf("$query shorts", "$query #shorts", query)
            SearchFilter.LIVE -> listOf("$query live", "$query siaran langsung", query)
            SearchFilter.RECENT -> listOf("$query terbaru", "$query hari ini", query)
        }
        val accept: (StreamInfoItem) -> Boolean = when (filter) {
            SearchFilter.ALL -> { _ -> true }
            SearchFilter.SHORTS -> { v ->
                v.isShortFormContent || (v.url ?: "").contains("/shorts/") || v.duration in 1..61
            }
            SearchFilter.LIVE -> { v -> v.streamType == StreamType.LIVE_STREAM }
            SearchFilter.RECENT -> { v -> isRecent(v) }
        }
        return FeedPager(queries.map { q -> { searchExtractor(q) } }, accept)
    }

    private fun isRecent(item: StreamInfoItem): Boolean {
        val date = runCatching { item.uploadDate?.offsetDateTime() }.getOrNull()
        if (date != null) return date.isAfter(OffsetDateTime.now().minusDays(7))
        val t = (item.textualUploadDate ?: "").lowercase()
        return listOf("menit", "jam", "hari", "minute", "hour", "day", "streamed", "live").any { t.contains(it) } &&
            !t.contains("minggu") && !t.contains("week") && !t.contains("bulan") && !t.contains("month") &&
            !t.contains("tahun") && !t.contains("year")
    }

    /** Search-as-you-type suggestions. */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        service.suggestionExtractor.suggestionList(query).take(8)
    }

    /**
     * Channel page data: banner, avatar, subscribers, description and its tabs. Looks the channel
     * up by name (via the url remembered from feeds when there is one).
     */
    suspend fun channelBundle(name: String): ChannelBundle? = withContext(Dispatchers.IO) {
        val wanted = name.trim()
        val url = ChannelCache.url(wanted) ?: run {
            val handler = service.searchQHFactory.fromQuery(wanted, listOf("channels"), "")
            val hits = SearchInfo.getInfo(service, handler).relatedItems
                .filterIsInstance<ChannelInfoItem>()
            (hits.firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) }
                ?: hits.firstOrNull())?.url
        } ?: return@withContext null
        val info = ChannelInfo.getInfo(service, url)
        val avatar = info.avatars.maxByOrNull { it.height }?.url ?: ChannelCache.avatar(wanted)
        val details = ChannelDetails(
            name = info.name?.ifBlank { null } ?: wanted,
            url = url,
            avatar = avatar,
            banner = info.banners.maxByOrNull { it.width }?.url,
            subscribers = info.subscriberCount,
            description = info.description.orEmpty(),
            verified = info.isVerified
        )
        ChannelCache.put(details.name, avatar, url)
        val tabs = HashMap<String, ListLinkHandler>()
        for (t in info.tabs) {
            val f = t.contentFilters
            for (key in listOf(ChannelTabs.VIDEOS, ChannelTabs.SHORTS, ChannelTabs.LIVESTREAMS)) {
                if (f.contains(key)) tabs[key] = t
            }
        }
        ChannelBundle(details, tabs)
    }

    /** Videos of one channel: search by channel name, keeping only that uploader's clips. */
    fun channelPager(name: String): FeedPager {
        val wanted = name.trim()
        return FeedPager(
            listOf(wanted, "$wanted video", "$wanted terbaru", "$wanted populer")
                .map { q -> { searchExtractor(q) } },
            accept = { it.uploaderName?.trim().equals(wanted, ignoreCase = true) }
        )
    }

    suspend fun home(): List<VideoItem> = withContext(Dispatchers.IO) {
        val fromKiosk = runCatching {
            val kiosk = service.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            kiosk.initialPage.items.filterIsInstance<StreamInfoItem>()
        }.getOrDefault(emptyList())

        val items = fromKiosk.ifEmpty { searchRaw("musik populer") }
        items.distinctBy { it.url }.map { it.toVideoItem() }
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        searchRaw(query).distinctBy { it.url }.map { it.toVideoItem() }
    }

    /**
     * Feed for the Shorts tab: runs a few short-video searches in parallel and keeps only
     * clips of <= ~60s (or /shorts/ urls), shuffled so every load feels different.
     */
    suspend fun shorts(): List<VideoItem> = withContext(Dispatchers.IO) {
        val picks = SHORT_QUERIES.shuffled().take(3)
        val raw = coroutineScope {
            picks.map { q -> async { runCatching { searchRaw(q) }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
        }
        val distinct = raw.distinctBy { it.url }
        val strict = distinct.filter {
            it.streamType != StreamType.LIVE_STREAM &&
                (it.url.contains("/shorts/") || it.duration in 1..61)
        }
        val pool = strict.ifEmpty { distinct.filter { it.duration in 1..120 } }
        pool.shuffled().map { it.toVideoItem() }
    }

    suspend fun related(item: VideoItem): List<VideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            searchRaw(item.channel.ifBlank { item.title })
                .filter { it.url != item.url }
                .distinctBy { it.url }
                .map { it.toVideoItem() }
        }.getOrDefault(emptyList())
    }

    private fun searchRaw(query: String): List<StreamInfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>()
    }

    /** Fetch every playable stream of a video so the player can offer quality options. */
    suspend fun resolveStreams(url: String): VideoStreams = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, url)

        val videoOnly = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(compareByDescending<VideoStream> { it.height }.thenByDescending { it.fps })
            .map { StreamTrack(it.height, it.fps, it.content, it.format?.suffix ?: "mp4", it.codec.orEmpty()) }

        val progressive = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(compareByDescending<VideoStream> { it.height }.thenByDescending { it.fps })
            .map { StreamTrack(it.height, it.fps, it.content, it.format?.suffix ?: "mp4", it.codec.orEmpty()) }

        val audioStream = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate })
            .firstOrNull()
        val audio = audioStream?.content

        VideoStreams(
            isLive = info.streamType == StreamType.LIVE_STREAM,
            hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            audioUrl = audio,
            videoOnly = videoOnly,
            progressive = progressive,
            audioExt = audioStream?.format?.suffix ?: "m4a"
        )
    }

    fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").take(80).trim()

    suspend fun downloadVideo(context: Context, url: String, title: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IOException("Storage unavailable")
                val file = File(dir, "${sanitizeFilename(title)}.mp4")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file
            }
        }
    }
}
