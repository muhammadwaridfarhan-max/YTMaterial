package com.example.ytclient.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.ui.components.VideoCard
import com.example.ytclient.ui.components.formatViews

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    controller: PlayerController,
    vm: MainViewModel,
    onMinimize: () -> Unit
) {
    val item = controller.current ?: return
    val context = LocalContext.current
    var showQualitySheet by remember { mutableStateOf(false) }

    BackHandler(onBack = onMinimize)
    LaunchedEffect(item.url) { vm.loadRelated(item) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            // --- Player area ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .statusBarsPadding()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = controller.player
                            useController = true
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val qualityLabel = if (controller.qualityHeight <= 0) {
                        stringResource(R.string.quality_auto)
                    } else "${controller.qualityHeight}p"
                    Surface(
                        onClick = { showQualitySheet = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            qualityLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (controller.isLoading || controller.isBuffering) {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }
                controller.error?.let { err ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            err,
                            color = Color(0xFFFF8A80),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = controller::retry) {
                            Text(
                                stringResource(R.string.retry),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // --- Details + up next ---
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!item.channelAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.channelAvatar,
                                        contentDescription = item.channel,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.channel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                formatViews(item.views)?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = { /* TODO: phase 4 - Google login */ },
                                shape = RoundedCornerShape(20.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(stringResource(R.string.subscribe))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.like)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.ThumbUp, contentDescription = null)
                                }
                            )
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.share)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Share,
                                        contentDescription = null
                                    )
                                }
                            )
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.download)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.up_next),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                when (val rs = vm.relatedState) {
                    null, UiState.Loading -> item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) { LoadingIndicator() }
                    }

                    is UiState.Error -> item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                rs.message,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { vm.loadRelated(item) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }

                    is UiState.Success -> items(rs.items, key = { it.url }) { video ->
                        VideoCard(video, onClick = { controller.open(video) })
                    }
                }
            }
        }
    }

    if (showQualitySheet) {
        val streams = controller.streams
        ModalBottomSheet(onDismissRequest = { showQualitySheet = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    stringResource(R.string.quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                if (streams == null) {
                    Text(
                        stringResource(R.string.err_no_stream),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else {
                    QualityRow(
                        label = stringResource(R.string.quality_auto),
                        selected = controller.qualityHeight == -1
                    ) {
                        controller.setQuality(-1)
                        showQualitySheet = false
                    }
                    streams.heights.forEach { h ->
                        QualityRow(
                            label = "${h}p",
                            selected = controller.qualityHeight == h
                        ) {
                            controller.setQuality(h)
                            showQualitySheet = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (selected) {
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
