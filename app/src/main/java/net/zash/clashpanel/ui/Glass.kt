package net.zash.clashpanel.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Frosted glass + immersive light, ported from the HarmonyOS app (common/Glass.ets).
 *
 * Blur: a "backdrop" is a GraphicsLayer that records what a capture box draws every frame (the root background,
 * or background + pages). A glass surface draws that layer, translated to its own position and clipped to its shape,
 * through a RenderEffect blur (+ saturation boost for the custom material), then its translucent tint on top.
 * Real blur needs RenderEffect (Android 12 / API 31+); below that surfaces fall back to a more opaque translucent tint.
 *
 * Light: per-surface edge highlight (stroke brighter toward the light), diagonal soft sheen and an accent glow shadow;
 * the light direction can follow the gravity sensor.
 */

val BLUR_SUPPORTED: Boolean = Build.VERSION.SDK_INT >= 31

/** card = normal card; row = long-list item; plain = inputs/chips (own border); top = header; bar = floating bar */
enum class GlassKind { Card, Row, Plain, Top, Bar }

@Immutable
data class GlassCfg(
    val on: Boolean = false,
    /** custom | thin | regular | thick */
    val style: String = "custom",
    /** 0..100 */
    val blur: Int = 45,
    val rows: Boolean = false,
    val lightOn: Boolean = false,
    /** 0..1 */
    val intensity: Float = 0.6f,
    val glow: Boolean = true,
    /** Light direction: degrees clockwise from the top of the screen */
    val lightDeg: Int = 330,
)

val LocalGlass = compositionLocalOf { GlassCfg() }

/** A captured layer plus where its capture box sits in root coordinates. */
class Backdrop {
    var layer: GraphicsLayer? = null
    var origin: Offset = Offset.Zero
}

/** Background only (wallpaper / gradient): what cards blur. */
val LocalBgBackdrop = staticCompositionLocalOf<Backdrop?> { null }
/** Background + pages: what the header and floating bar blur. */
val LocalContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val b = remember { Backdrop() }
    if (BLUR_SUPPORTED) b.layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    return b
}

/** Content shown in another window (sheet, dialog, menu): no backdrop to blur there. */
@Composable
fun NoBackdrop(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBgBackdrop provides null, LocalContentBackdrop provides null, content = content)
}

/** Records what this box draws into the backdrop layer (every frame) and draws it. */
fun Modifier.captureBackdrop(b: Backdrop): Modifier =
    this.onGloballyPositioned { b.origin = it.positionInRoot() }
        .drawWithContent {
            val l = b.layer
            if (l == null) {
                drawContent()
            } else {
                l.record { this@drawWithContent.drawContent() }
                drawLayer(l)
            }
        }

private fun lightVec(deg: Int): Offset {
    val r = Math.toRadians(deg.toDouble())
    return Offset(sin(r).toFloat(), -cos(r).toFloat())
}

/** Blur radius in dp for a kind (HarmonyOS: radius = 4 + blur * 0.8, header/bar >= 60/70; scaled for Android). */
private fun blurDp(c: GlassCfg, kind: GlassKind): Float {
    val f = when (c.style) { "thin" -> 0.6f; "thick" -> 1.4f; else -> 1f }
    val base = (4 + c.blur * 0.55f) * f
    return when (kind) {
        GlassKind.Top -> max(30f, base)
        GlassKind.Bar -> max(36f, base)
        else -> base
    }
}

private fun makeEffect(radiusPx: Float, saturation: Float): RenderEffect? {
    if (!BLUR_SUPPORTED || radiusPx < 0.5f) return null
    val blur = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
    if (saturation == 1f) return blur.asComposeRenderEffect()
    val cm = ColorMatrix().apply { setSaturation(saturation) }
    val sat = android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
    return android.graphics.RenderEffect.createChainEffect(sat, blur).asComposeRenderEffect()
}

/**
 * Glass surface: blur of the matching backdrop + tint + light. Use instead of background(); content is drawn on top.
 * [tint] overrides the base color (cards default to the palette card color).
 */
fun Modifier.glass(kind: GlassKind, shape: Shape, tint: Color? = null, border: Boolean = true): Modifier = composed {
    val c = LocalGlass.current
    val p = LocalPal.current
    val chrome = kind == GlassKind.Top || kind == GlassKind.Bar
    val src = if (chrome) LocalContentBackdrop.current else LocalBgBackdrop.current
    val blurOn = when {
        src?.layer == null -> false
        chrome -> true // header / bar always blur what scrolls underneath (lightly when glass is off)
        !c.on -> false
        kind == GlassKind.Row || kind == GlassKind.Plain -> c.rows
        else -> true
    }
    val I = c.intensity.coerceIn(0f, 1f)
    val light = c.lightOn && I > 0f
    val density = LocalDensity.current
    val cardAlpha = p.card.alpha

    // ---------- base color ----------
    val bg: Color = tint ?: if (chrome) {
        val base = if (p.dark) Color(0xFF1C1F24) else Color.White
        val a = if (c.on) {
            val minA = if (kind == GlassKind.Bar) (if (p.dark) 0.62f else 0.66f) else (if (p.dark) 0.55f else 0.58f)
            max(minA, min(0.9f, cardAlpha * 0.9f)).let { if (BLUR_SUPPORTED) it else max(it, 0.88f) }
        } else if (kind == GlassKind.Bar) 0.97f else 0.96f
        base.copy(alpha = a)
    } else if (kind == GlassKind.Plain) Color.Transparent else {
        if (c.on && !BLUR_SUPPORTED) p.card.copy(alpha = max(cardAlpha, 0.78f)) else p.card
    }

    val radiusPx = with(density) { (if (c.on || !chrome) blurDp(c, kind) else 16f).dp.toPx() }
    val sat = if (!c.on) 1f else if (c.style == "custom") (if (p.dark) 1.3f else 1.5f) else 1.15f
    val effect = remember(blurOn, radiusPx, sat) { if (blurOn) makeEffect(radiusPx, sat) else null }
    val child = if (effect != null) rememberGraphicsLayer0() else null
    val pos = remember { mutableStateOf(Offset.Zero) }

    // ---------- shadow / glow ----------
    val lv = lightVec(c.lightDeg)
    var m: Modifier = this
    when (kind) {
        GlassKind.Bar -> m = if (light && c.glow) m.shadow((14 + 10 * I).dp, shape, clip = false, ambientColor = p.accent.copy(alpha = 0.5f * I), spotColor = p.accent.copy(alpha = (if (p.dark) 0.9f else 0.7f) * I))
        else m.shadow(10.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.3f))
        GlassKind.Card -> if (light && c.glow) m = m.shadow((6 + 10 * I).dp, shape, clip = false, ambientColor = p.accent.copy(alpha = 0.4f * I), spotColor = p.accent.copy(alpha = (if (p.dark) 0.8f else 0.5f) * I))
        else if (light) m = m.shadow((4 + 6 * I).dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.12f * I), spotColor = Color.Black.copy(alpha = 0.18f * I))
        else if (c.on) m = m.shadow(3.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (p.dark) 0.25f else 0.10f), spotColor = Color.Black.copy(alpha = if (p.dark) 0.3f else 0.12f))
        else -> {}
    }

    if (child != null) m = m.onGloballyPositioned { pos.value = it.positionInRoot() }

    m.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val s = src
        val l = s?.layer
        if (child != null && l != null) {
            val o = pos.value - s.origin
            child.renderEffect = effect
            child.record { translate(-o.x, -o.y) { drawLayer(l) } }
            clipPath(path) { drawLayer(child) }
        }
        drawPath(path, bg)
        if (light) {
            // Soft light: a white gradient sweeping in diagonally from the light side
            val sheenA = (if (p.dark) 0.10f else 0.30f) * I * (if (c.on) 1f else 0.6f)
            val mid = if (chrome) 0.65f else 0.5f
            val half = Offset(size.width / 2, size.height / 2)
            val reach = (size.width * kotlin.math.abs(lv.x) + size.height * kotlin.math.abs(lv.y)) / 2
            val start = half + lv * reach
            val end = half - lv * reach
            drawPath(path, Brush.linearGradient(
                0f to Color.White.copy(alpha = sheenA), mid * 0.5f to Color.White.copy(alpha = sheenA * 0.25f),
                mid to Color.Transparent, 1f to Color.Transparent, start = start, end = end,
            ))
        }
    }.let { mm ->
        val ownsBorder = border && (kind == GlassKind.Card || kind == GlassKind.Row || kind == GlassKind.Bar)
        if (!ownsBorder || !(light || c.on)) mm else mm.drawWithContent {
            drawContent()
            // Edge highlight: brighter toward the light, dimmer away from it (glass thickness)
            val hi = if (light) (if (p.dark) 0.42f else 0.95f) * I else 0f
            val baseA = if (c.on) (if (p.dark) 0.10f else 0.45f) else 0f
            val half = Offset(size.width / 2, size.height / 2)
            val reach = (size.width * kotlin.math.abs(lv.x) + size.height * kotlin.math.abs(lv.y)) / 2
            val sw = 1.dp.toPx()
            val outline = shape.createOutline(Size(size.width - sw, size.height - sw), layoutDirection, this)
            val path = Path().apply { addOutline(outline); translate(Offset(sw / 2, sw / 2)) }
            drawPath(path, Brush.linearGradient(
                0f to Color.White.copy(alpha = (baseA + hi).coerceIn(0f, 1f)),
                0.5f to Color.White.copy(alpha = baseA.coerceIn(0f, 1f)),
                1f to Color.White.copy(alpha = (baseA * 0.3f).coerceIn(0f, 1f)),
                start = half + lv * reach, end = half - lv * reach,
            ), style = Stroke(sw))
        }
    }
}

@Composable
private fun rememberGraphicsLayer0(): GraphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()

/**
 * With glass on and no wallpaper: a soft accent gradient background so blur and light are visible
 * (same composition as Index.glassBackdrop in the HarmonyOS app).
 */
@Composable
fun GlassBackdrop(lightOn: Boolean, lightDeg: Int) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().drawBehind {
        val w = size.width; val h = size.height
        // linear 160deg
        drawRect(Brush.linearGradient(
            0f to (if (p.dark) Color(0xFF15171C) else Color(0xFFF7F8FB)),
            0.55f to lerp(if (p.dark) Color(0xFF111316) else Color(0xFFF4F4F6), p.accent, if (p.dark) 0.18f else 0.16f),
            1f to (if (p.dark) Color(0xFF0E1013) else Color(0xFFEEF0F4)),
            start = Offset(w * 0.32f, 0f), end = Offset(w * 0.68f, h),
        ))
        val cx = (if (lightOn) 30 + sin(Math.toRadians(lightDeg.toDouble())).toFloat() * 18 else 22f) / 100f
        val r1 = max(w, h) * 0.75f * 0.75f
        drawRect(Brush.radialGradient(
            listOf(p.accent.copy(alpha = if (p.dark) 0.38f else 0.30f), p.accent.copy(alpha = 0f)),
            center = Offset(w * cx, h * 0.18f), radius = r1,
        ))
        drawRect(Brush.radialGradient(
            listOf(p.up.copy(alpha = if (p.dark) 0.30f else 0.26f), p.up.copy(alpha = 0f)),
            center = Offset(w * 0.88f, h * 0.72f), radius = max(w, h) * 0.7f * 0.75f,
        ))
    })
}

/** Plain translucent background when a component should not draw glass (helper for widgets). */
fun Modifier.surface(color: Color, shape: Shape) = this.background(color, shape)
