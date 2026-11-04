package com.example.ytclient.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import dev.chrisbanes.haze.HazeState

/**
 * Floating top bar in the MeeTube style: a glass title pill on the left and round glass buttons on
 * the right, instead of one full-width bar.
 *
 * Shrink on scroll: as the feed scrolls down, the bar compacts (smaller height, lower elevation)
 * instead of jumping away. It returns to the expanded height when you scroll back to the top.
 *
 * @param scrollOffset pixels the content has scrolled from the top; > 0 scrolls down, < 0 scrolls up.
 */
@Composable
fun FloatingTopBar(
    visible: Boolean,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    blurStrength: Int = 20,
    blurEnabled: Boolean = true,
    scrollOffset: Float = 0f
) {
    val shrink by animateFloatAsState(
        targetValue = clamp(scrollOffset / SHRINK_DISTANCE, 0f, 1f),
        animationSpec = tween(120),
        label = "topBarShrink"
    )
    // 80 dp (expanded at the top) -> 48 dp (shrunk). 48 dp is the minimum comfortable size.
    val barHeight = 80.dp - shrink * 32.dp
    val elevation = 12.dp - shrink * 10.dp

    Box(modifier = modifier.height(barHeight)) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                .glass(
                    hazeState, CircleShape,
                    elevation = elevation,
                    blurRadius = blurStrength.dp,
                    blurEnabled = blurEnabled
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title pill (kept at a fixed 48 dp so text never gets cramped)
            Box(
                Modifier
                    .height(48.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .glass(
                        hazeState, CircleShape,
                        elevation = elevation,
                        blurRadius = blurStrength.dp,
                        blurEnabled = blurEnabled
                    )
            ) {
                GlassContent {
                    Row(
                        Modifier
                            .height(48.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF0000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(hazeState, blurStrength, blurEnabled, onSearch) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
                Spacer(Modifier.width(8.dp))
                GlassCircleButton(hazeState, blurStrength, blurEnabled, onProfile) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = stringResource(R.string.nav_you)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    hazeState: HazeState?,
    blurStrength: Int,
    blurEnabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(48.dp)
            .glass(
                hazeState, CircleShape,
                elevation = 8.dp,
                blurRadius = blurStrength.dp,
                blurEnabled = blurEnabled
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassContent { IconButton(onClick = onClick) { icon() } }
    }
}

private const val SHRINK_DISTANCE = 64f

private fun clamp(value: Float, min: Float = 0f, max: Float = 1f): Float {
    return value.coerceIn(min, max)
}
