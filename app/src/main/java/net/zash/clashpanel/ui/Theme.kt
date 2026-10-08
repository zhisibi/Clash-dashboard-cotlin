package net.zash.clashpanel.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** App palette, same values as the HarmonyOS app (common/Theme.ets). */
data class Palette(
    val bg: Color = Color(0xFFF4F4F6),
    val card: Color = Color.White,
    val chip: Color = Color(0xFFF1F2F4),
    val subtle: Color = Color(0xFF8A8F98),
    val good: Color = Color(0xFF3FA36B),
    val warn: Color = Color(0xFFDB9A1F),
    val bad: Color = Color(0xFFE05050),
    val up: Color = Color(0xFF7C5CFF),
    val down: Color = Color(0xFF16A6E8),
    val text: Color = Color(0xFF1D2025),
    val primary: Color = Color(0xFF2B3036),
    val onPrimary: Color = Color.White,
    val outline: Color = Color(0xFFD5D8DD),
    val outlineVariant: Color = Color(0xFFE8EAED),
    val sheet: Color = Color(0xFFF4F4F6),
    val info: Color = Color(0xFF38BDF8),
    /** Accent color (buttons, selection, switches, charts, ...) */
    val accent: Color = Color(0xFF0A59F7),
    val onAccent: Color = Color.White,
    /** Soft accent background (selection, highlighted chips) */
    val accentSoft: Color = Color(0x240A59F7),
    val dark: Boolean = false,
    /** Opaque card color (menus, dialogs) */
    val solidCard: Color = Color.White,
)

typealias ExtraColors = Palette

val LocalPal = staticCompositionLocalOf { Palette() }
val LocalExtra get() = LocalPal

/** Preset accent colors; nameKey is an i18n key. Same names/colors as the HarmonyOS app. */
data class AccentDef(val key: String, val nameKey: String, val light: Color, val dark: Color)

val ACCENTS = listOf(
    AccentDef("blue", "accent_blue", Color(0xFF0A59F7), Color(0xFF3F7CFF)),
    AccentDef("orange", "accent_orange", Color(0xFFF39A2B), Color(0xFFF7AE52)),
    AccentDef("cat", "accent_cat", Color(0xFF3F5683), Color(0xFF7088B8)),
    AccentDef("red", "accent_red", Color(0xFFCF0A2C), Color(0xFFEE4A61)),
    AccentDef("purple", "accent_purple", Color(0xFF8A5CD1), Color(0xFFA47FE0)),
    AccentDef("pink", "accent_pink", Color(0xFFFB7299), Color(0xFFFC8DAE)),
    AccentDef("green", "accent_green", Color(0xFF7BC23E), Color(0xFF93D15E)),
)

fun findAccent(key: String): AccentDef = ACCENTS.firstOrNull { it.key == key } ?: ACCENTS[0]

private val lightBase = Palette()
private val darkBase = Palette(
    bg = Color(0xFF111316), card = Color(0xFF1C1F24), chip = Color(0xFF262A31), subtle = Color(0xFF8D939C),
    good = Color(0xFF52C487), warn = Color(0xFFF0B544), bad = Color(0xFFF06A6A),
    up = Color(0xFF9B85FF), down = Color(0xFF40BDF5), text = Color(0xFFE6E8EB),
    primary = Color(0xFFE6E8EB), onPrimary = Color(0xFF15181C),
    outline = Color(0xFF3A3F47), outlineVariant = Color(0xFF2C3037), sheet = Color(0xFF111316),
    dark = true, solidCard = Color(0xFF1C1F24),
)

/**
 * Builds the palette like Store.palette() in the HarmonyOS app: base light/dark + accent; with frosted glass on the page
 * background is drawn by the root (wallpaper or soft gradient) and cards are translucent; with a wallpaper only,
 * cards use the wallpaper card opacity.
 */
fun buildPalette(dark: Boolean, accentKey: String, glassOn: Boolean, glassAlpha: Float, wallpaper: Boolean, cardAlpha: Float): Palette {
    val base = if (dark) darkBase else lightBase
    val a = findAccent(accentKey)
    val c = if (dark) a.dark else a.light
    var p = base.copy(
        accent = c, onAccent = Color.White, accentSoft = c.copy(alpha = if (dark) 0.24f else 0.14f),
        primary = c, onPrimary = Color.White,
        // Traffic chart: download uses the accent, upload a lighter tint
        down = c, up = lerp(c, Color.White, if (dark) 0.45f else 0.5f),
    )
    if (glassOn) {
        p = p.copy(
            bg = Color.Transparent, card = p.card.copy(alpha = glassAlpha),
            chip = p.chip.copy(alpha = (glassAlpha + 0.15f).coerceAtMost(1f)),
            sheet = p.sheet.copy(alpha = (glassAlpha + 0.2f).coerceAtMost(0.92f)),
        )
    } else if (wallpaper) {
        p = p.copy(bg = Color.Transparent, card = p.card.copy(alpha = cardAlpha), chip = p.chip.copy(alpha = (cardAlpha + 0.1f).coerceAtMost(1f)))
    }
    return p
}

@Composable
fun MimiTheme(p: Palette, content: @Composable () -> Unit) {
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
        secondary = Color(0xFF9AA4B2), background = Color(0xFF111316), surface = p.solidCard,
        surfaceVariant = Color(0xFF262A31), onSurface = p.text, onBackground = p.text, onSurfaceVariant = p.subtle,
        surfaceContainer = p.solidCard, surfaceContainerLow = p.solidCard, surfaceContainerHigh = Color(0xFF23272D),
        surfaceContainerHighest = Color(0xFF2A2F36), surfaceContainerLowest = Color(0xFF15181C),
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.text,
        outline = p.outline, outlineVariant = p.outlineVariant, error = p.bad,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
        secondary = Color(0xFF5C6470), background = Color(0xFFF4F4F6), surface = p.solidCard,
        surfaceVariant = Color(0xFFF1F2F4), onSurface = p.text, onBackground = p.text, onSurfaceVariant = p.subtle,
        surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFF7F7F9),
        surfaceContainerHighest = Color(0xFFEEEFF2), surfaceContainerLowest = Color.White,
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.text,
        outline = p.outline, outlineVariant = p.outlineVariant, error = p.bad,
    )
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography()) {
            CompositionLocalProvider(LocalContentColor provides p.text, content = content)
        }
    }
}
