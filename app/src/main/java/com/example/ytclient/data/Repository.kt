package com.example.ytclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

data class VideoItem(
    val title: String,
    val url: String,
    val channel: String,
    val channelAvatar: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val isLive: Boolean
)

data class StreamTrack(val height: Int, val fps: Int, val url: String)

data class VideoStreams(
    val isLive: Boolean,
    val hlsUrl: String?,
    val audioUrl: String?,
    val videoOnly: List<StreamTrack>,
    val progressive: List<StreamTrack>
) {
    val heights: List<Int>
        get() = (videoOnly.map { it.height } + progressive.map { it.height })
            .distinct().sortedDescending()
}

/**
 * Endless feed: walks page by page through one source (kiosk / search) and, when that source
 * runs dry, moves on to the next one so the list never hits a dead end.
 */
class FeedPager(private val sources: List<() -> ListExtractor<out InfoItem>>) {
    private var index = -1
    private var current: ListExtractor<out InfoItem>? = null
    private var next: Page? = null
    private var loadedAny = false
    private val seen = HashSet<String>()

    @Volatile
    var exhausted = false
        private set

    /** Next batch of new videos; empty only when every source is used up. */
    suspend fun nextBatch(): List<VideoItem> = withContext(Dispatchers.IO) {
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
            val fresh = raw.filter { it.url != null && seen.add(it.url) }
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

internal fun StreamInfoItem.toVideoItem() = VideoItem(
    title = name ?: "",
    url = url ?: "",
    channel = uploaderName ?: "",
    channelAvatar = runCatching {
        (uploaderAvatars.filter { it.height >= 48 }.minByOrNull { it.height }
            ?: uploaderAvatars.maxByOrNull { it.height })?.url
    }.getOrNull(),
    // Mid-size thumbnail (>= 270px tall) instead of the biggest one: far lighter to decode while scrolling.
    thumbnail = (thumbnails.filter { it.height >= 270 }.minByOrNull { it.height }
        ?: thumbnails.maxByOrNull { it.height })?.url,
    durationSec = duration,
    views = viewCount,
    uploaded = textualUploadDate,
    isLive = streamType == StreamType.LIVE_STREAM
)

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
    fun homePager() = FeedPager(
        listOf<() -> ListExtractor<out InfoItem>>({ service.kioskList.defaultKioskExtractor }) +
            HOME_EXTRA.map { q -> { searchExtractor(q) } }
    )

    /** Endless results for a query (also used by the home filter chips). */
    fun searchPager(query: String) = FeedPager(
        listOf(query, "$query populer", "$query terbaru", "$query hari ini", "best $query")
            .map { q -> { searchExtractor(q) } }
    )

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
            .map { StreamTrack(it.height, it.fps, it.content) }

        val progressive = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(compareByDescending<VideoStream> { it.height }.thenByDescending { it.fps })
            .map { StreamTrack(it.height, it.fps, it.content) }

        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate })
            .firstOrNull()?.content

        VideoStreams(
            isLive = info.streamType == StreamType.LIVE_STREAM,
            hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            audioUrl = audio,
            videoOnly = videoOnly,
            progressive = progressive
        )
    }
}
