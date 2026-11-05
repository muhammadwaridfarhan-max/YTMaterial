package com.example.ytclient.ui

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Reusable "liquid glass" building blocks for surfaces that are NOT floating over the page
 * (cards, sheets, dialogs, settings groups). Floating bars keep using [glass] (Haze / lens).
 *
 * - API 31+: a translucent tint over whatever is behind; callers that own a backdrop (e.g. the
 *   player's ambient glow) get real blur from that backdrop being drawn with Modifier.blur.
 * - Older APIs: Modifier.blur is a no-op there, so we raise the tint alpha so text stays readable.
 * - A thin top-lit highlight border (white -> transparent) sells the glass edge on both.
 */

private val blurSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Tint used for glass cards/sheets/dialogs; more opaque when blur is unavailable. */
@Composable
fun glassTint(strength: Float = 1f): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val base = if (blurSupported) (if (dark) 0.58f else 0.66f) else (if (dark) 0.90f else 0.94f)
    return scheme.surfaceContainerHigh.copy(alpha = (base * strength).coerceIn(0f, 1f))
}

/** Top-lit specular edge: brighter at the top, fading out toward the bottom. */
@Composable
fun glassHighlightBrush(): Brush {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val top = Color.White.copy(alpha = if (dark) 0.28f else 0.70f)
    val mid = Color.White.copy(alpha = if (dark) 0.06f else 0.18f)
    val bottom = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    return Brush.verticalGradient(listOf(top, mid, bottom))
}

/** Translucent glass fill + highlight border, clipped to [shape]. */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = glassTint(),
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(tint, shape)
    .border(borderWidth, glassHighlightBrush(), shape)

/** Glass card with a springy press-scale when [onClick] is set. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val clickMod = if (onClick != null) {
        Modifier
            .pressScale(interaction, 0.97f)
            .glassSurface(shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
    } else {
        Modifier.glassSurface(shape)
    }
    GlassContent {
        Column(modifier.then(clickMod).padding(contentPadding), content = content)
    }
}

/** Container color for ModalBottomSheet / AlertDialog so they read as glass. */
@Composable
fun glassSheetColor(): Color = glassTint(if (blurSupported) 1.45f else 1.05f)

/** Wrap sheet/dialog content to add the highlight edge on top of the translucent container. */
@Composable
fun Modifier.glassEdge(shape: Shape): Modifier = this.border(1.dp, glassHighlightBrush(), shape)

/**
 * Ambient light for the player: a heavily blurred, scaled-up copy of [imageUrl] plus a gradient
 * glow from its Palette colors. Colors and image crossfade when the video changes.
 * On API < 31 (no Modifier.blur), only the palette gradient is drawn (a blurry image is faked by
 * the soft gradient), so nothing looks sharp or noisy behind the content.
 */
@Composable
fun AmbientGlow(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    intensity: Float = 1f
) {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.primary
    val fallback2 = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    var c1 by remember { mutableStateOf(fallback) }
    var c2 by remember { mutableStateOf(fallback2) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(96) // tiny: palette only needs a few pixels
            .allowHardware(false) // Palette must read pixels
            .build()
        val result = context.imageLoader.execute(request)
        val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(bmp).maximumColorCount(12).generate() }
        val vib = palette.vibrantSwatch ?: palette.dominantSwatch
        val alt = palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        vib?.let { c1 = Color(it.rgb) }
        alt?.let { c2 = Color(it.rgb) }
    }

    val a1 by animateColorAsState(c1, tween(900), label = "ambient1")
    val a2 by animateColorAsState(c2, tween(900), label = "ambient2")
    val dark = surface.luminance() < 0.5f
    val glowAlpha = (if (dark) 0.55f else 0.35f) * intensity

    Box(modifier.clip(RoundedCornerShape(0.dp))) {
        if (blurSupported && !imageUrl.isNullOrBlank()) {
            Crossfade(targetState = imageUrl, animationSpec = tween(700), label = "ambientImg") { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(72.dp)
                        .alpha(if (dark) 0.55f * intensity else 0.40f * intensity)
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(a1.copy(alpha = glowAlpha), a2.copy(alpha = glowAlpha * 0.6f), Color.Transparent)
                    )
                )
        )
        // Fade into the surface so content below stays on a calm background.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.6f), surface)))
        )
    }
}

/** Section header + glass group used by Settings / account screens. */
@Composable
fun GlassSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            androidx.compose.material3.Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp, top = 4.dp)
            )
        }
        GlassCard(shape = RoundedCornerShape(24.dp), contentPadding = 4.dp, content = content)
    }
}

/** True when the Liquid Glass setting is on; screens use it to switch plain surfaces to glass. */
@Composable
fun liquidGlassOn(): Boolean = LocalGlassStyle.current.liquid

/**
 * Glass when Liquid Glass is on, otherwise the plain [fallback] background (the old look).
 * Reads composables at call time only, never inside lambdas.
 */
@Composable
fun Modifier.glassOr(shape: Shape, fallback: Color, strength: Float = 1f): Modifier {
    return if (liquidGlassOn()) {
        val tint = glassTint(strength)
        this.glassSurface(shape, tint)
    } else {
        this.clip(shape).background(fallback, shape)
    }
}
