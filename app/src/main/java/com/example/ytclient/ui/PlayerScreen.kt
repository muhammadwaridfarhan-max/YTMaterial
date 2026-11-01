package com.example.ytclient.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.ytclient.data.PlaySource
import com.example.ytclient.data.Repository
import com.example.ytclient.data.VideoItem

private sealed interface PlayState {
    data object Loading : PlayState
    data object Ready : PlayState
    data class Error(val message: String) : PlayState
}

private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

@OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(item: VideoItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PlayState>(PlayState.Loading) }
    var attempt by remember { mutableStateOf(0) }
    val player = remember { ExoPlayer.Builder(context).build() }

    BackHandler(onBack = onBack)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                state = PlayState.Error(error.message ?: "Gagal memutar video")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(item.url, attempt) {
        state = PlayState.Loading
        runCatching { Repository.resolveSource(item.url) }
            .onSuccess { src ->
                val dsf = DefaultHttpDataSource.Factory()
                    .setUserAgent(UA)
                    .setAllowCrossProtocolRedirects(true)
                fun prog(url: String): MediaSource =
                    ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))
                val media: MediaSource = when (src) {
                    is PlaySource.Hls -> HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(src.url))
                    is PlaySource.Progressive -> prog(src.url)
                    is PlaySource.Merged -> MergingMediaSource(prog(src.videoUrl), prog(src.audioUrl))
                }
                player.setMediaSource(media)
                player.prepare()
                player.playWhenReady = true
                state = PlayState.Ready
            }
            .onFailure { state = PlayState.Error(it.message ?: "Gagal memuat video") }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                            useController = true
                        }
                    }
                )
                if (state is PlayState.Loading) LoadingIndicator()
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.channel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                (state as? PlayState.Error)?.let { err ->
                    Text(
                        err.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Button(onClick = { attempt++ }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Coba lagi")
                    }
                }
            }
        }
    }
}
