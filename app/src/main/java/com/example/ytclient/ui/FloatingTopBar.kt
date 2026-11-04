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
        Box(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .glass(
                    hazeState,
                    RoundedCornerShape(28.dp),
                    elevation = 10.dp,
                    blurRadius = blurStrength.dp,
                    blurEnabled = blurEnabled
                )
        ) {
            GlassContent {
                val onSurface = MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFFF0000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = onSurface
                        )
                    }
                    IconButton(onClick = onProfile) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = stringResource(R.string.nav_you),
                            tint = onSurface
                        )
                    }
                }
            }
        }
    }
}
