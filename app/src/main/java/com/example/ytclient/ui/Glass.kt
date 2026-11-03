package com.example.ytclient.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 */
@Composable
fun Modifier.glass(hazeState: HazeState?, shape: Shape, elevation: Dp = 12.dp, blurStrength: Dp = 20.dp): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = this.shadow(elevation, shape).clip(shape)
    return if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(scheme.surfaceContainerHigh.copy(alpha = 0.62f)),
                blurRadius = blurStrength,
                noiseFactor = 0.03f
            )
        )
    } else {
        base.background(scheme.surfaceContainerHigh)
    }
}
