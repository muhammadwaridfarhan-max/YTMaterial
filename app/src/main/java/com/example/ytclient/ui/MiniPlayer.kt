package com.example.ytclient.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem
import com.example.ytclient.ui.components.WavyProgress
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Remembers the last item so the pill can still draw itself while it animates out after close(). */
private class LastItem {
    var value: VideoItem? = null
}

/** Floating glass "media pill": springs in, swipe sideways to dismiss, springs back when released. */
@Composable
fun MiniPlayer(
    controller: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    blurStrength: Int = 20,
    blurEnabled: Boolean = true
) {
    val live = controller.current
    val last = remember { LastItem() }
    if (live != null) last.value = live

    AnimatedVisibility(
        visible = live != null && !controller.expanded,
        modifier = modifier,
        enter = slideInVertically(
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
        ) { it } + scaleIn(
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
            initialScale = 0.85f
        ) + fadeIn(tween(150)),
        exit = slideOutVertically(
            spring(stiffness = Spring.StiffnessMedium)
        ) { it } + scaleOut(tween(180), targetScale = 0.9f) + fadeOut(tween(160))
    ) {
        val item = last.value ?: return@AnimatedVisibility
        val scope = rememberCoroutineScope()
        val dragX = remember { Animatable(0f) }
        var widthPx by remember { mutableFloatStateOf(1f) }
        var progress by remember { mutableFloatStateOf(0f) }
        val interaction = remember { MutableInteractionSource() }

        LaunchedEffect(controller) {
            while (true) {
                val d = controller.player.duration
                progress = if (d > 0) {
                    (controller.player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
                } else 0f
                delay(300)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    widthPx = size.width
                    translationX = dragX.value
                    val f = (abs(dragX.value) / size.width).coerceIn(0f, 1f)
                    alpha = 1f - f
                    val s = 1f - 0.05f * f
                    scaleX = s
                    scaleY = s
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
                                        if (dragX.value > 0) widthPx else -widthPx,
                                        spring(stiffness = Spring.StiffnessMedium)
                                    )
                                    controller.close()
                                } else {
                                    dragX.animateTo(
                                        0f,
                                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragX.animateTo(
                                    0f,
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    )
                }
                .glass(
                    hazeState,
                    RoundedCornerShape(28.dp),
                    elevation = 10.dp,
                    blurRadius = blurStrength.dp,
                    blurEnabled = blurEnabled
                )
        ) {
            GlassContent {
                val scheme = MaterialTheme.colorScheme
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(interaction, 0.97f)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onExpand
                            )
                            .padding(start = 10.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(scheme.surfaceContainerHigh)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.channel,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalIconButton(
                            onClick = controller::togglePlay,
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                if (controller.isPlayingState) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (controller.isPlayingState) R.string.pause else R.string.play
                                ),
                                tint = scheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = controller::close) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = scheme.onSurface
                            )
                        }
                    }
                    WavyProgress(
                        progress = progress,
                        animate = controller.isPlayingState,
                        activeColor = scheme.primary,
                        trackColor = scheme.outlineVariant,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                        height = 8.dp,
                        strokeWidth = 2.5.dp,
                        amplitude = 1.5.dp,
                        wavelength = 16.dp
                    )
                }
            }
        }
    }
}
