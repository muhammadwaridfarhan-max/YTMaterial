package com.example.ytclient.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import java.util.Random
import kotlin.math.min

/*
 * Liquid glass, ported from MeeTube's GlassView (github.com/yashoncode/meetube) to Compose.
 * Pipeline, same numbers as MeeTube / BitChord:
 *   1. API 31+: the live page behind the bar, saturated 1.5x and blurred;
 *   2. API 33+: a circle-profile refraction lens (24dp deep, chromatic dispersion) over it;
 *   3. a tint, fine grain and a specular rim (additive white, lit at 45 degrees).
 * The refraction and highlight AGSL shaders are Kyant's backdrop v2.0.0
 * (Copyright 2025 Kyant, Apache License 2.0), as vendored by BitChord and MeeTube.
 */

private const val SATURATION = 1.5f
private const val REFRACTION_HEIGHT_DP = 24f
private const val REFRACTION_AMOUNT_DP = 24f
private const val HIGHLIGHT_ANGLE_DEG = 45.0
private const val NOISE_ALPHA = 0.04f

/** The page the glass bars frost. Recorded once, sampled live by every bar. */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the source sits in the root, so a bar knows which part of it is behind itself. */
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Put on the box that holds the page; every liquid glass bar draws what this records. */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop, enabled: Boolean): Modifier =
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        this
    } else {
        this
            .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                backdrop.layer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(backdrop.layer)
            }
    }

private class EffectCache {
    var w = -1f
    var h = -1f
    var r = -1f
    var blur = -1f
    var lens = false
    var rim: ShaderBrush? = null
}

/** Glass surface for API 31+: live backdrop + (API 33) lens + tint + grain + rim. */
@Composable
internal fun Modifier.liquidGlassSurface(
    backdrop: GlassBackdrop,
    shape: Shape,
    blurRadius: Dp,
    lens: Boolean,
    tint: Color
): Modifier {
    val effectLayer = rememberGraphicsLayer()
    val cache = remember { EffectCache() }
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithContent {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) {
                drawContent()
                return@drawWithContent
            }
            val radius = cornerPx(shape, size, layoutDirection, this)
            val blurPx = blurRadius.toPx()
            val dens = this.density
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (cache.w != w || cache.h != h || cache.r != radius ||
                    cache.blur != blurPx || cache.lens != lens
                ) {
                    cache.w = w
                    cache.h = h
                    cache.r = radius
                    cache.blur = blurPx
                    cache.lens = lens
                    effectLayer.renderEffect = buildEffect(w, h, radius, dens, blurPx, lens)
                    effectLayer.clip = true
                    cache.rim = ShaderBrush(rimShader(w, h, radius))
                }
                // Everything behind this bar, shifted so the right slice lands under it.
                effectLayer.record {
                    translate(left = backdrop.origin.x - pos.x, top = backdrop.origin.y - pos.y) {
                        drawLayer(backdrop.layer)
                    }
                }
                drawLayer(effectLayer)
            }
            drawRect(tint)
            drawRect(brush = noiseBrush, alpha = NOISE_ALPHA)
            cache.rim?.let { rim ->
                drawRoundRect(
                    brush = rim,
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width = dens),
                    blendMode = BlendMode.Plus
                )
            }
            drawContent()
        }
}

private fun cornerPx(shape: Shape, size: Size, dir: LayoutDirection, density: Density): Float {
    val maxR = min(size.width, size.height) / 2f
    return when (val outline = shape.createOutline(size, dir, density)) {
        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x.coerceAtMost(maxR)
        is Outline.Rectangle -> 0f
        else -> maxR
    }
}

/** Saturate, blur, then (API 33+) refract the blurred backdrop. */
@RequiresApi(Build.VERSION_CODES.S)
private fun buildEffect(
    w: Float,
    h: Float,
    radius: Float,
    density: Float,
    blurPx: Float,
    lens: Boolean
): androidx.compose.ui.graphics.RenderEffect {
    val saturate = ColorMatrix().apply { setSaturation(SATURATION) }
    val frost = PlatformRenderEffect.createBlurEffect(
        blurPx,
        blurPx,
        PlatformRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturate)),
        Shader.TileMode.CLAMP
    )
    val result = if (lens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = RuntimeShader(REFRACTION_AGSL)
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("refractionHeight", REFRACTION_HEIGHT_DP * density)
        shader.setFloatUniform("refractionAmount", -REFRACTION_AMOUNT_DP * density)
        shader.setFloatUniform("depthEffect", 1f)
        shader.setFloatUniform("chromaticAberration", 1f)
        PlatformRenderEffect.createChainEffect(
            PlatformRenderEffect.createRuntimeShaderEffect(shader, "content"),
            frost
        )
    } else {
        frost
    }
    return result.asComposeRenderEffect()
}

private fun rimShader(w: Float, h: Float, radius: Float): Shader =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        highlightShader(w, h, radius)
    } else {
        // No runtime shaders: light from above, a faint bounce at the bottom.
        LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0x66FFFFFF, 0x14FFFFFF, 0x0AFFFFFF, 0x2EFFFFFF),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun highlightShader(w: Float, h: Float, radius: Float): Shader {
    val shader = RuntimeShader(HIGHLIGHT_AGSL)
    shader.setFloatUniform("size", w, h)
    shader.setFloatUniform("radius", radius)
    shader.setColorUniform("color", 0x80FFFFFF.toInt())
    shader.setFloatUniform("angle", Math.toRadians(HIGHLIGHT_ANGLE_DEG).toFloat())
    shader.setFloatUniform("falloff", 1f)
    return shader
}

private val noiseBrush: ShaderBrush by lazy {
    val size = 96
    val px = IntArray(size * size)
    val random = Random(7)
    for (i in px.indices) {
        val v = random.nextInt(256)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
}

private val ROUNDED_RECT_SDF = """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private val REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
""" + ROUNDED_RECT_SDF + """
float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    return color;
}
"""

private val HIGHLIGHT_AGSL = """
uniform float2 size;
uniform float radius;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;
""" + ROUNDED_RECT_SDF + """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}
"""
