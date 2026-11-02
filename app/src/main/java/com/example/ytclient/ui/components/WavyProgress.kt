package com.example.ytclient.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private fun DrawScope.drawWavy(
    progress: Float,
    buffered: Float,
    phase: Float,
    amplitude: Float,
    wavelength: Float,
    strokeWidth: Float,
    activeColor: Color,
    trackColor: Color,
    bufferColor: Color,
    showThumb: Boolean,
    thumbWidth: Float,
    thumbHeight: Float
) {
    val cy = size.height / 2f
    val w = size.width
    val pad = strokeWidth / 2f
    val activeX = (pad + (w - 2 * pad) * progress.coerceIn(0f, 1f))
    val bufX = (pad + (w - 2 * pad) * buffered.coerceIn(0f, 1f))

    drawLine(trackColor, Offset(activeX, cy), Offset(w - pad, cy), strokeWidth, StrokeCap.Round)
    if (bufX > activeX) {
        drawLine(bufferColor, Offset(activeX, cy), Offset(bufX, cy), strokeWidth, StrokeCap.Round)
    }
    val path = Path()
    val step = 3f
    var x = pad
    path.moveTo(x, cy + amplitude * sin(phase))
    while (x < activeX) {
        x = (x + step).coerceAtMost(activeX)
        val y = cy + amplitude * sin((2f * PI.toFloat() * (x - pad) / wavelength) + phase)
        path.lineTo(x, y)
    }
    drawPath(
        path,
        activeColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    if (showThumb) {
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(activeX - thumbWidth / 2f, cy - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f)
        )
    }
}

@Composable
fun WavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    activeColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    strokeWidth: Dp = 3.dp,
    amplitude: Dp = 2.dp,
    wavelength: Dp = 20.dp,
    height: Dp = 10.dp
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phaseAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val amp by animateFloatAsState(if (animate) 1f else 0f, tween(300), label = "amp")
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawWavy(
            progress = progress, buffered = 0f,
            phase = if (animate) phaseAnim else 0f,
            amplitude = amplitude.toPx() * amp,
            wavelength = wavelength.toPx(),
            strokeWidth = strokeWidth.toPx(),
            activeColor = activeColor, trackColor = trackColor, bufferColor = trackColor,
            showThumb = false, thumbWidth = 0f, thumbHeight = 0f
        )
    }
}

@Composable
fun WavySeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrubbing: (Boolean) -> Unit = {},
    activeColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.28f),
    bufferColor: Color = Color.White.copy(alpha = 0.45f)
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }

    val fraction = if (dragging) dragFraction
    else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val transition = rememberInfiniteTransition(label = "wave")
    val phaseAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val waveOn = isPlaying && !dragging
    val amp by animateFloatAsState(if (waveOn) 1f else 0f, tween(350), label = "amp")
    val thumbH by animateFloatAsState(if (dragging) 28f else 20f, tween(150), label = "thumb")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) onSeek((offset.x / widthPx).coerceIn(0f, 1f).times(duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onScrubbing(true)
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (duration > 0) onSeek((dragFraction * duration).toLong())
                        dragging = false
                        onScrubbing(false)
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubbing(false)
                    }
                )
            }
    ) {
        widthPx = size.width
        drawWavy(
            progress = fraction, buffered = bufFraction,
            phase = phaseAnim,
            amplitude = 3.dp.toPx() * amp,
            wavelength = 26.dp.toPx(),
            strokeWidth = 4.dp.toPx(),
            activeColor = activeColor, trackColor = trackColor, bufferColor = bufferColor,
            showThumb = true, thumbWidth = 4.dp.toPx(), thumbHeight = thumbH.dp.toPx()
        )
    }
}
