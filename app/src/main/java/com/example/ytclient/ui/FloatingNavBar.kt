package com.example.ytclient.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

data class NavTab(@StringRes val label: Int, val icon: ImageVector)

/** Floating pill-shaped bottom bar. Blur strength and on/off state come from [AppSettings]. */
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
    // Blur animation: animate blur radius from 0 to requested strength
    val blurRadius by animateFloatAsState(
        targetValue = if (blurEnabled) blurStrength.toFloat() else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        ),
        label = "navBlur"
    )

    Box(modifier = modifier.glass(hazeState, CircleShape, blurRadius = blurRadius.dp, blurEnabled = blurEnabled)) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                NavPill(
                    tab = tab,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    blurEnabled = blurEnabled
                )
            }
        }
    }
}

@Composable
private fun NavPill(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    blurEnabled: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillIcon"
    )
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = tween(160),
        label = "pillContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "pillContent"
    )

    Row(
        modifier = Modifier
            .height(48.dp)
            .pressScale(interaction, 0.92f)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { }
            .padding(horizontal = if (selected) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = if (selected) null else stringResource(tab.label),
            tint = content,
            modifier = Modifier
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                .size(26.dp)
        )
        AnimatedContent(
            targetState = selected,
            label = "pillLabel",
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.padding(start = 8.dp)
        ) { isSelected ->
            if (isSelected) {
                Text(
                    stringResource(tab.label),
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
