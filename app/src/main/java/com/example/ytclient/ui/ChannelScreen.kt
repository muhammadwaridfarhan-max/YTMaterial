package com.example.ytclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.data.ChannelDetails
import com.example.ytclient.data.SubscriptionManager
import com.example.ytclient.data.VideoItem
import com.example.ytclient.settings.AppSettings
import com.example.ytclient.settings.SettingsManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import android.icu.text.CompactDecimalFormat
import java.util.Locale

@Composable
fun ChannelScreen(
    channel: String,
    vm: MainViewModel,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    val subs by SubscriptionManager.channels.collectAsState(initial = emptyList())
    val settings by SettingsManager.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    LaunchedEffect(channel) { vm.loadChannel(channel) }

    val details = vm.channelDetails
    val title = details?.name ?: channel
    val subscribed = channel in subs || title in subs
    val state = vm.channelState ?: UiState.Loading
    val loaded = (state as? UiState.Success)?.items.orEmpty()
    val avatar = details?.avatar ?: loaded.firstOrNull { !it.channelAvatar.isNullOrBlank() }?.channelAvatar
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    val header: @Composable () -> Unit = {
        ChannelHeader(
            name = title,
            details = details,
            avatar = avatar,
            subscribed = subscribed,
            onToggle = { scope.launch { SubscriptionManager.toggle(channel) } },
            tabs = vm.channelTabs,
            selected = vm.channelTab,
            onSelect = vm::selectChannelTab
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            if (vm.channelTab == ChannelTab.ABOUT) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = topInset + 4.dp, bottom = bottomInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") { header() }
                    item(key = "about") { AboutCard(details) }
                }
            } else {
                VideoList(
                    state = state,
                    onRetry = { vm.loadChannel(channel) },
                    onPlay = onPlay,
                    modifier = Modifier.fillMaxSize(),
                    topPadding = topInset,
                    bottomPadding = bottomInset,
                    header = header,
                    onLoadMore = vm::loadMoreChannel,
                    loadingMore = vm.channelLoadingMore
                )
            }
        }

        // Floating glass pill with the back button and the channel name.
        Box(
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
        ) {
            GlassContent {
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    name: String,
    details: ChannelDetails?,
    avatar: String?,
    subscribed: Boolean,
    onToggle: () -> Unit,
    tabs: List<ChannelTab>,
    selected: ChannelTab,
    onSelect: (ChannelTab) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        // Banner (falls back to a Material You gradient when the channel has none).
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 5.5f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))
                )
        ) {
            if (details?.banner != null) {
                AsyncImage(
                    model = details.banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = scheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (details?.verified == true) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (details != null && details.subscribers >= 0) {
                    Text(
                        stringResource(R.string.channel_subscribers, compactCount(details.subscribers)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (!details?.description.isNullOrBlank()) {
                    Text(
                        details!!.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { onSelect(ChannelTab.ABOUT) }
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (subscribed) {
            FilledTonalButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text(stringResource(R.string.subscribed)) }
        } else {
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) { Text(stringResource(R.string.subscribe)) }
        }
        Spacer(Modifier.height(10.dp))
        val glassChips = liquidGlassOn()
        val chipGlass = glassTint()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs, key = { it.name }) { tab ->
                FilterChip(
                    colors = if (glassChips) androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = chipGlass) else androidx.compose.material3.FilterChipDefaults.filterChipColors(),
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    label = {
                        Text(
                            stringResource(
                                when (tab) {
                                    ChannelTab.VIDEOS -> R.string.channel_tab_videos
                                    ChannelTab.SHORTS -> R.string.channel_tab_shorts
                                    ChannelTab.LIVE -> R.string.channel_tab_live
                                    ChannelTab.ABOUT -> R.string.channel_tab_about
                                }
                            )
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AboutCard(details: ChannelDetails?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.channel_tab_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val text = details?.description?.ifBlank { null }
                ?: stringResource(R.string.channel_no_description)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (details != null && details.subscribers >= 0) {
                Text(
                    stringResource(R.string.channel_subscribers, compactCount(details.subscribers)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

internal fun compactCount(n: Long): String =
    CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
        .format(n)
