package com.example.ytclient.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Tanda "sedang scroll". [ping] dipanggil tiap ada delta scroll; [active] true selama scroll
 * berjalan lalu mati ~120 ms setelah berhenti. Dibaca hanya di fase draw (tanpa recomposition).
 */
@Stable
class ScrollActivity {
    var tick by mutableIntStateOf(0)
        private set
    var active by mutableStateOf(false)

    fun ping() {
        tick++
    }
}

val LocalScrollActivity = staticCompositionLocalOf { ScrollActivity() }

/**
 * Frosted-glass surface: blurs whatever is drawn behind it (Android 12+), tints it with Material You
 * surface colors and adds a thin light edge. Older Android versions get a plain opaque surface.
 *
 * Blur dihitung pada separuh resolusi dan dimatikan selama list di-scroll (kaca tetap berwarna),
 * karena blur ulang tiap frame adalah penyebab utama scroll berat.
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
    val activity = LocalScrollActivity.current
    val allowBlur = blurEnabled
    val base = this
        .shadow(elevation, shape)
        .clip(shape)
        .border(0.8.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
    return if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(scheme.surfaceContainerHigh.copy(alpha = 0.80f)),
                blurRadius = blurRadius,
                noiseFactor = 0f
            )
        ) {
            this.blurEnabled = allowBlur && !activity.active
            this.inputScale = HazeInputScale.Fixed(0.5f)
        }
    } else {
        base.background(scheme.surfaceContainerHigh)
    }
}

/** Sets the content color for text/icons on glass to the theme's onSurface (readable in light and dark). */
@Composable
fun GlassContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        androidx.compose.material3.LocalTextStyle provides
            androidx.compose.material3.LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        content = content
    )
}
