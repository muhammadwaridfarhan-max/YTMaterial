package com.example.ytclient.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Light frosted-glass surface: blurs whatever is drawn behind it (Android 12+) and tints it with
 * Material You surface colors. Older Android versions get a plain opaque surface instead.
 *
 * NOTE: a Modifier cannot set the content color, so wrap whatever you put inside a glass
 * container with [GlassContent] (otherwise Text/Icon fall back to black in dark mode).
 *
 * @param blurRadius animated blur radius in Dp (0 = no blur). Supports smooth animation.
 * @param blurEnabled whether blur is currently enabled. When false, blur animates to 0.
 */
@Composable
fun Modifier.glass(
    hazeState: HazeState?,
    shape: Shape,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 20.dp,
    blurEnabled: Boolean = true
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = this.shadow(elevation, shape).clip(shape)
    val styled = if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val effectiveRadius = if (blurEnabled) blurRadius else 0.dp
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(scheme.surfaceContainerHigh.copy(alpha = 0.62f)),
                blurRadius = effectiveRadius,
                noiseFactor = 0.03f
            )
        )
    } else {
        base.background(scheme.surfaceContainerHigh)
    }
    // Thin glass edge, like MeeTube's liquid glass.
    return styled.border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f), shape)
}

/** Content color for anything drawn on a glass surface: always readable in light and dark. */
@Composable
fun GlassContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        content = content
    )
}
