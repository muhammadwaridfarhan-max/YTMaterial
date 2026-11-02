package com.example.ytclient.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.ui.components.WavyProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(
    controller: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = controller.current != null && !controller.expanded,
        modifier = modifier,
        enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        val item = controller.current ?: return@AnimatedVisibility
        val scope = rememberCoroutineScope()
        val dragX = remember { Animatable(0f) }
        var widthPx by remember { mutableFloatStateOf(1f) }
        var progress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(controller) {
            while (true) {
                val d = controller.player.duration
                progress = if (d > 0) {
                    (controller.player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
                } else 0f
                delay(300)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    widthPx = size.width
                    translationX = dragX.value
                    alpha = 1f - (abs(dragX.value) / size.width).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { dragX.snapTo(dragX.value + delta) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (abs(dragX.value) > widthPx * 0.35f) {
                                    dragX.animateTo(
                                        if (dragX.value > 0) widthPx else -widthPx
                                    )
                                    controller.close()
                                    dragX.snapTo(0f)
                                } else {
                                    dragX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f) } }
                    )
                },
            tonalElevation = 3.dp,
            shadowElevation = 10.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .height(68.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.channel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = controller::togglePlay) {
                        Icon(
                            if (controller.isPlayingState) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (controller.isPlayingState) R.string.pause else R.string.play
                            )
                        )
                    }
                    IconButton(onClick = controller::close) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                WavyProgress(
                    progress = progress,
                    animate = controller.isPlayingState,
                    activeColor = Color(0xFFFF2D3C),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    height = 8.dp,
                    strokeWidth = 2.5.dp,
                    amplitude = 1.5.dp,
                    wavelength = 16.dp
                )
            }
        }
    }
}
