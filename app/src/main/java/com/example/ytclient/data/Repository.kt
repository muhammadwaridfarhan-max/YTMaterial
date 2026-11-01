package com.example.ytclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

data class VideoItem(
    val title: String,
    val url: String,
    val channel: String,
    val thumbnail: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val isLive: Boolean
)

sealed interface PlaySource {
    data class Hls(val url: String) : PlaySource
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaySource
    data class Progressive(val url: String) : PlaySource
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

    private fun searchRaw(query: String): List<StreamInfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>()
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        title = name ?: "",
        url = url ?: "",
        channel = uploaderName ?: "",
        thumbnail = thumbnails.maxByOrNull { it.height }?.url,
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        isLive = streamType == StreamType.LIVE_STREAM
    )

    suspend fun resolveSource(url: String): PlaySource = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, url)
        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }

        if (info.streamType == StreamType.LIVE_STREAM && hls != null) {
            return@withContext PlaySource.Hls(hls)
        }

        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A }
            .maxByOrNull { it.averageBitrate }
        val video = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 && it.height <= 720 }
            .maxByOrNull { it.height }
        if (audio != null && video != null) {
            return@withContext PlaySource.Merged(video.content, audio.content)
        }

        val progressive = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.height }
        if (progressive != null) return@withContext PlaySource.Progressive(progressive.content)

        if (hls != null) return@withContext PlaySource.Hls(hls)
        error("Tidak ada stream yang bisa diputar")
    }
}
