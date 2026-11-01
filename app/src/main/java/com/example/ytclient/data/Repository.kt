package com.example.ytclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
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
}
