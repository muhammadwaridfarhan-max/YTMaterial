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
 * Frosted-glass surface: blurs whatever is drawn behind it (Android 12+), tints it with Material You
 * surface colors and adds a thin light edge. Older Android versions get a plain opaque surface.
 *
 * Put the content in [GlassContent]: a bare Box does not provide a content color, which is why
 * text turned black on the dark theme.
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
    val base = this
        .shadow(elevation, shape)
        .clip(shape)
        .border(0.8.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
    return if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val effectiveRadius = if (blurEnabled) blurRadius else 0.dp
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(scheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                blurRadius = effectiveRadius,
                noiseFactor = 0.03f
            )
        )
    } else {
        base.background(scheme.surfaceContainerHigh)
    }
}

/** Sets the content color for text/icons on glass to the theme's onSurface (readable in light and dark). */
@Composable
fun GlassContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        content = content
    )
}
