package com.example.ytclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
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

object Repository {
    private val service get() = ServiceList.YouTube

    suspend fun home(): List<VideoItem> = withContext(Dispatchers.IO) {
        val fromKiosk = runCatching {
            val kiosk = service.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            kiosk.initialPage.items.filterIsInstance<StreamInfoItem>()
        }.getOrDefault(emptyList())

        val items = fromKiosk.ifEmpty { searchRaw("musik populer") }
        items.map { it.toVideoItem() }
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        searchRaw(query).map { it.toVideoItem() }
    }

    suspend fun related(item: VideoItem): List<VideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            searchRaw(item.channel.ifBlank { item.title })
                .filter { it.url != item.url }
                .map { it.toVideoItem() }
        }.getOrDefault(emptyList())
    }

    private fun searchRaw(query: String): List<StreamInfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>()
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        title = name ?: "",
        url = url ?: "",
        channel = uploaderName ?: "",
        channelAvatar = runCatching { uploaderAvatars.maxByOrNull { it.height }?.url }.getOrNull(),
        thumbnail = thumbnails.maxByOrNull { it.height }?.url,
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        isLive = streamType == StreamType.LIVE_STREAM
    )

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
