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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
 * Floating top bar in the MeeTube style: a glass title pill on the left and round glass buttons on
 * the right, instead of one full-width bar. Springs in, slides away while scrolling down.
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
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title pill
            Box(
                Modifier.glass(
                    hazeState, CircleShape,
                    elevation = 8.dp,
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
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                }
                Spacer(Modifier.width(8.dp))
                GlassCircleButton(hazeState, blurStrength, blurEnabled, onProfile) {
                    Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_you))
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
        GlassContent {
            IconButton(onClick = onClick) { icon() }
        }
    }
}
