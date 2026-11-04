package com.example.ytclient.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

data class NavTab(@StringRes val label: Int, val icon: ImageVector)

/**
 * Floating pill-shaped bottom bar drawn over the content. Blur strength (Dp) and on/off come from
 * settings; the blur radius is applied directly (no spring) so dragging the slider stays cheap.
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
    Box(
        modifier = modifier.glass(
            hazeState,
            CircleShape,
            blurRadius = blurStrength.dp,
            blurEnabled = blurEnabled
        )
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                NavPill(tab = tab, selected = index == selected, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun NavPill(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val t by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
        label = "pill"
    )
    val interaction = remember { MutableInteractionSource() }
    val container = scheme.primaryContainer
    val onContainer = scheme.onPrimaryContainer
    val idle = scheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .height(44.dp)
            .pressScale(interaction, 0.94f)
            .clip(CircleShape)
            // alpha is read in the draw phase, so the animation does not recompose the bar
            .drawBehind { drawRect(container.copy(alpha = t)) }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = stringResource(tab.label),
            tint = lerp(idle, onContainer, t),
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180))
        ) {
            Text(
                stringResource(tab.label),
                color = onContainer,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
