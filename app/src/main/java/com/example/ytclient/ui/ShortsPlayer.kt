package com.example.ytclient.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.ytclient.data.Repository
import com.example.ytclient.data.VideoItem
import com.example.ytclient.data.VideoStreams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One looping ExoPlayer shared by every page of the Shorts pager. Only the settled page is
 * attached to it; the next clips' stream URLs are resolved ahead of time so a swipe starts fast.
 */
class ShortsPlayer(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private val cache = LinkedHashMap<String, VideoStreams>()

    var currentUrl by mutableStateOf<String?>(null)
        private set
    var hasFirstFrame by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var isPortrait by mutableStateOf(true)
        private set
    var userPaused by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasFirstFrame = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0) isPortrait = videoSize.height >= videoSize.width
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.message ?: "Gagal memutar video"
            }
        })
        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    val d = player.duration
                    progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                }
                delay(120)
            }
        }
    }

    fun play(item: VideoItem) {
        if (currentUrl == item.url && error == null) {
            resume()
            return
        }
        currentUrl = item.url
        hasFirstFrame = false
        error = null
        userPaused = false
        progress = 0f
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        loadJob = scope.launch {
            try {
                val streams = streamsFor(item.url)
                if (currentUrl != item.url) return@launch
                player.setMediaSource(buildSource(streams))
                player.prepare()
                player.playWhenReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (currentUrl == item.url) error = t.message ?: "Gagal memuat video"
            }
        }
    }

    fun retry(item: VideoItem) {
        cache.remove(item.url)
        currentUrl = null
        play(item)
    }

    /** Resolve stream URLs of upcoming clips in the background (one at a time). */
    fun prefetch(items: List<VideoItem>) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            for (item in items) {
                if (cache.containsKey(item.url)) continue
                try {
                    streamsFor(item.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
            userPaused = true
        } else {
            player.play()
            userPaused = false
        }
    }

    fun pause() = player.pause()

    fun resume() {
        if (!userPaused && currentUrl != null) player.play()
    }

    fun release() {
        scope.cancel()
        player.release()
    }

    private suspend fun streamsFor(url: String): VideoStreams {
        cache[url]?.let { return it }
        val resolved = Repository.resolveStreams(url)
        cache[url] = resolved
        if (cache.size > 16) cache.remove(cache.keys.first())
        return resolved
    }

    private fun buildSource(s: VideoStreams): MediaSource {
        val dsf = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        fun progressive(url: String): MediaSource =
            ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))

        // Muxed file starts fastest; use it when it is at least 480p, otherwise merge video + audio.
        val muxed = s.progressive.firstOrNull { it.height <= 720 }
        if (muxed != null && muxed.height >= 480) return progressive(muxed.url)

        val video = s.videoOnly.firstOrNull { it.height <= 720 }
        if (video != null && s.audioUrl != null) {
            return MergingMediaSource(progressive(video.url), progressive(s.audioUrl))
        }
        if (muxed != null) return progressive(muxed.url)
        s.progressive.lastOrNull()?.let { return progressive(it.url) }
        if (s.hlsUrl != null) {
            return HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(s.hlsUrl))
        }
        throw IllegalStateException("No playable stream found")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
