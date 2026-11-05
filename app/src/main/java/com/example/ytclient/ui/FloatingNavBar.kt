package com.example.ytclient.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavTab(@StringRes val label: Int, val icon: ImageVector)

/**
 * iOS 26-style liquid glass tab bar: a floating capsule with icon + label tabs and a glass
 * "slider" pill that springs between tabs (stretching while it travels). The pill can also be
 * dragged across the bar; releasing snaps to the nearest tab and selects it.
 */
@Composable
fun FloatingNavBar(
    tabs: List<NavTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    blurStrength: Int = 20,
    blurEnabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val pillFill = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f)
    val pillEdge = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.35f else 0.85f),
            Color.White.copy(alpha = if (dark) 0.05f else 0.25f)
        )
    )
    val active = scheme.onSurface
    val idle = scheme.onSurfaceVariant
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Pill position in "tab index" units (fractional while animating / dragging).
    val pos = remember { Animatable(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!dragging) {
            pos.animateTo(
                selected.toFloat(),
                spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Box(
        modifier = modifier.glass(
            hazeState,
            CircleShape,
            blurRadius = blurStrength.dp,
            blurEnabled = blurEnabled
        )
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(6.dp)
        ) {
            val count = tabs.size.coerceAtLeast(1)
            val tabW = maxWidth / count
            val tabWPx = with(density) { tabW.toPx() }

            // Sliding glass pill. Offset/scale are read in the layer block (draw phase only).
            Box(
                Modifier
                    .width(tabW)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val p = pos.value
                        translationX = p * tabWPx
                        // Stretch while travelling between tabs (distance to nearest whole tab).
                        val frac = abs(p - p.roundToInt())
                        val stretch = (if (dragging) 0.10f else 0f) + frac * 0.5f
                        scaleX = 1f + stretch.coerceAtMost(0.28f)
                        scaleY = 1f - stretch.coerceAtMost(0.28f) * 0.25f
                    }
                    .padding(horizontal = 2.dp)
                    .background(pillFill, CircleShape)
                    .border(1.dp, pillEdge, CircleShape)
            )

            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count, tabWPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val target = pos.value.roundToInt().coerceIn(0, count - 1)
                                scope.launch {
                                    pos.animateTo(
                                        target.toFloat(),
                                        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                                if (target != currentSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnSelect(target)
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { pos.animateTo(currentSelected.toFloat(), spring()) }
                            }
                        ) { change, dx ->
                            change.consume()
                            val next = (pos.value + dx / tabWPx).coerceIn(0f, (count - 1).toFloat())
                            scope.launch { pos.snapTo(next) }
                        }
                    },
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavItem(
                        tab = tab,
                        weight = { 1f - (abs(pos.value - index)).coerceIn(0f, 1f) },
                        active = active,
                        idle = idle,
                        modifier = Modifier.width(tabW).fillMaxHeight(),
                        onClick = {
                            if (index != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    weight: () -> Float,
    active: Color,
    idle: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val t = weight()
    val tint = lerp(idle, active, t)
    Column(
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val s = 1f + 0.08f * weight()
                    scaleX = s
                    scaleY = s
                }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(tab.label),
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (t > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
