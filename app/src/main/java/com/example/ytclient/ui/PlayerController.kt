package com.example.ytclient.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.ytclient.data.Repository
import com.example.ytclient.data.VideoItem
import com.example.ytclient.data.VideoStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shared playback state so the player survives navigation between tabs:
 * full-screen player when [expanded] is true, mini player above the nav bar otherwise.
 */
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    var current by mutableStateOf<VideoItem?>(null)
        private set
    var streams by mutableStateOf<VideoStreams?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var isPlayingState by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Selected video height; -1 = Auto (best quality capped at 720p for smooth playback). */
    var qualityHeight by mutableIntStateOf(-1)
        private set

    var expanded by mutableStateOf(false)

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.message
            }
        })
    }

    fun open(item: VideoItem) {
        if (current?.url != item.url) {
            current = item
            qualityHeight = -1
            streams = null
            error = null
            load()
        }
        expanded = true
    }

    fun retry() = load()

    fun setQuality(height: Int) {
        if (qualityHeight == height) return
        qualityHeight = height
        if (streams != null) applySource()
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun close() {
        job?.cancel()
        player.stop()
        player.clearMediaItems()
        current = null
        streams = null
        error = null
        expanded = false
    }

    fun release() {
        scope.cancel()
        player.release()
    }

    private fun load() {
        val item = current ?: return
        job?.cancel()
        job = scope.launch {
            isLoading = true
            error = null
            runCatching { Repository.resolveStreams(item.url) }
                .onSuccess { result ->
                    streams = result
                    applySource()
                }
                .onFailure {
                    isLoading = false
                    error = it.message
                }
        }
    }

    private fun applySource() {
        val s = streams ?: return
        try {
            player.setMediaSource(buildSource(s))
            player.prepare()
            player.playWhenReady = true
            error = null
        } catch (t: Throwable) {
            error = t.message
        } finally {
            isLoading = false
        }
    }

    private fun buildSource(s: VideoStreams): MediaSource {
        val dsf = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        fun progressive(url: String): MediaSource =
            ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))

        if (s.isLive && s.hlsUrl != null) {
            return HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(s.hlsUrl))
        }

        val cap = if (qualityHeight <= 0) 720 else qualityHeight
        val video = s.videoOnly.firstOrNull { it.height <= cap } ?: s.videoOnly.lastOrNull()
        if (video != null && s.audioUrl != null) {
            return MergingMediaSource(progressive(video.url), progressive(s.audioUrl))
        }

        val progressiveTrack =
            s.progressive.firstOrNull { it.height <= cap } ?: s.progressive.lastOrNull()
        if (progressiveTrack != null) return progressive(progressiveTrack.url)

        if (s.hlsUrl != null) {
            return HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(s.hlsUrl))
        }
        error("No playable stream found")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
