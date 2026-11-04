package com.example.ytclient.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import dev.chrisbanes.haze.HazeState

/**
 * Floating glass top bar. Springs in with a little overshoot, slides away on scroll-down and
 * comes back on scroll-up (the visibility flag is driven from App).
 */
@Composable
fun FloatingTopBar(
    visible: Boolean,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    blurStrength: Int = 20,
    blurEnabled: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
        ) { -it * 2 } + fadeIn(tween(120)),
        exit = slideOutVertically(
            spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntOffset(1, 1))
        ) { -it * 2 } + fadeOut(tween(90))
    ) {
        val scheme = MaterialTheme.colorScheme
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MeeTube-style compact brand pill (44dp, fully rounded), Material You accent mark.
            Box(
                Modifier.glass(
                    hazeState, RoundedCornerShape(22.dp), elevation = 6.dp,
                    blurRadius = blurStrength.dp, blurEnabled = blurEnabled
                )
            ) {
                GlassContent {
                    Row(
                        Modifier.height(44.dp).padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = scheme.onPrimary, modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GlassCircleButton(
                onClick = onSearch, hazeState = hazeState, blurStrength = blurStrength,
                blurEnabled = blurEnabled, icon = Icons.Filled.Search,
                description = stringResource(R.string.search)
            )
            Spacer(Modifier.width(8.dp))
            GlassCircleButton(
                onClick = onProfile, hazeState = hazeState, blurStrength = blurStrength,
                blurEnabled = blurEnabled, icon = Icons.Filled.Person,
                description = stringResource(R.string.nav_you)
            )
        }
    }
}

@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    hazeState: HazeState?,
    blurStrength: Int,
    blurEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    Box(
        Modifier.glass(
            hazeState, androidx.compose.foundation.shape.CircleShape, elevation = 6.dp,
            blurRadius = blurStrength.dp, blurEnabled = blurEnabled
        )
    ) {
        GlassContent {
            IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
                Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
