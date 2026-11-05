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
import androidx.compose.ui.graphics.luminance
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
 * Pilihan gaya kaca dari Settings. [liquid]: kaca cair ala MeeTube (lensa pembias di Android 13+).
 * [live]: blur tetap hidup saat scroll (kalau false, blur dimatikan selama scroll agar ringan).
 * [backdrop] adalah halaman yang di-frosted oleh kaca cair; null berarti kaca cair tidak tersedia.
 */
@Stable
class GlassStyle(
    val liquid: Boolean = false,
    val live: Boolean = true,
    val backdrop: GlassBackdrop? = null
)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/**
 * Frosted-glass surface: blurs whatever is drawn behind it (Android 12+), tints it with Material You
 * surface colors and adds a thin light edge. Older Android versions get a plain opaque surface.
 *
 * Dengan opsi Liquid glass (Android 12+; lensa penuh di 13+) bar menggambar halaman di belakangnya
 * langsung dari layer sumber, lalu menambah pembiasan, grain, dan rim spekular seperti MeeTube.
 *
 * Opsi Live blur: bila mati, blur Haze dimatikan selama list di-scroll (kaca tetap berwarna),
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
    val style = LocalGlassStyle.current
    val backdrop = style.backdrop

    if (style.liquid && blurEnabled && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dark = scheme.surface.luminance() < 0.5f
        return this
            .shadow(elevation, shape)
            .clip(shape)
            .liquidGlassSurface(
                backdrop = backdrop,
                // MeeTube blurs 8dp; the strength setting scales around that (20 -> 8dp).
                shape = shape,
                blurRadius = (blurRadius.value * 0.4f).coerceIn(2f, 16f).dp,
                lens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                tint = scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.40f else 0.32f)
            )
    }

    val allowBlur = blurEnabled
    val live = style.live
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
            this.blurEnabled = allowBlur && (live || !activity.active)
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
