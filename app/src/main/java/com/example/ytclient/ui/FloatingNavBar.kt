package com.example.ytclient.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

data class NavTab(@StringRes val label: Int, val icon: ImageVector)

/** Floating glass tab bar (icons only, MeeTube style). The selected tab gets a Material You pill that pops in. */
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
        animationSpec = tween(200),
        label = "pill"
    )
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "pillPop"
    )
    val interaction = remember { MutableInteractionSource() }
    val container = scheme.primaryContainer
    val active = scheme.primary
    val idle = scheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .width(68.dp)
            .height(44.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            // alpha is read in the draw phase, so the animation does not recompose the bar
            .drawBehind { drawRect(container.copy(alpha = 0.9f * t)) }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = stringResource(tab.label),
            tint = lerp(idle, active, t),
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
        )
    }
}
