package com.example.ytclient.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

data class NavTab(@StringRes val label: Int, val icon: ImageVector)

/**
 * Floating pill-shaped bottom bar. Colors come only from [MaterialTheme.colorScheme],
 * so it keeps following Material You (wallpaper / custom seed) automatically.
 */
@Composable
fun FloatingNavBar(
    tabs: List<NavTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    blurStrength: Dp = 20.dp
) {
    Box(modifier = modifier.glass(hazeState, CircleShape, blurStrength = blurStrength)) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                NavPill(tab, selected = index == selected, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun NavPill(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = SoftSpring,
        label = "pillIcon"
    )
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = tween(180),
        label = "pillContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "pillContent"
    )
    Row(
        modifier = Modifier
            .animateContentSize(tween(180))
            .height(48.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = if (selected) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = if (selected) null else stringResource(tab.label),
            tint = content,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(200)) + expandHorizontally(),
            exit = fadeOut(tween(100)) + shrinkHorizontally()
        ) {
            Text(
                stringResource(tab.label),
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
