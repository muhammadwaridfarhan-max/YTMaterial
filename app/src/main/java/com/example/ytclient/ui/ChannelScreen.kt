package com.example.ytclient.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.data.SubscriptionManager
import com.example.ytclient.data.VideoItem
import com.example.ytclient.settings.AppSettings
import com.example.ytclient.settings.SettingsManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(
    channel: String,
    vm: MainViewModel,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    val subs by SubscriptionManager.channels.collectAsState(initial = emptyList())
    val subscribed = channel in subs
    val settings by SettingsManager.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    LaunchedEffect(channel) { vm.loadChannel(channel) }

    val state = vm.channelState ?: UiState.Loading
    val loaded = (state as? UiState.Success)?.items.orEmpty()
    val avatar = loaded.firstOrNull { !it.channelAvatar.isNullOrBlank() }?.channelAvatar
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            VideoList(
                state = state,
                onRetry = { vm.loadChannel(channel) },
                onPlay = onPlay,
                modifier = Modifier.fillMaxSize(),
                topPadding = topInset,
                bottomPadding = bottomInset,
                header = {
                    ChannelHeader(
                        name = channel,
                        avatar = avatar,
                        count = loaded.size,
                        subscribed = subscribed,
                        onToggle = { scope.launch { SubscriptionManager.toggle(channel) } }
                    )
                },
                onLoadMore = vm::loadMoreChannel,
                loadingMore = vm.channelLoadingMore
            )
        }

        // Floating glass pill with the back button and the channel name.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .glass(
                    hazeState,
                    CircleShape,
                    elevation = 10.dp,
                    blurRadius = settings.blurStrength.dp,
                    blurEnabled = settings.blurEnabled
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.close)
                )
            }
            Text(
                text = channel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun ChannelHeader(
    name: String,
    avatar: String?,
    count: Int,
    subscribed: Boolean,
    onToggle: () -> Unit
) {
    val pop by animateFloatAsState(
        targetValue = if (avatar != null) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "avatarPop"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                AsyncImage(
                    model = avatar,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (count > 0) {
            Text(
                stringResource(R.string.channel_videos, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        if (subscribed) {
            OutlinedButton(onClick = onToggle) { Text(stringResource(R.string.subscribed)) }
        } else {
            Button(onClick = onToggle) { Text(stringResource(R.string.subscribe)) }
        }
    }
}
