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
    // 1.2.1: secondary/status colors darkened so text reaches WCAG 4.5:1 on cards, chips and translucent glass cards
    val subtle: Color = Color(0xFF5A5E66),
    val good: Color = Color(0xFF296945),
    val warn: Color = Color(0xFF7C5712),
    val bad: Color = Color(0xFFB62020),
    val up: Color = Color(0xFF7C5CFF),
    val down: Color = Color(0xFF16A6E8),
    val text: Color = Color(0xFF1D2025),
    val primary: Color = Color(0xFF2B3036),
    val onPrimary: Color = Color.White,
    val outline: Color = Color(0xFFD5D8DD),
    val outlineVariant: Color = Color(0xFFE8EAED),
    val sheet: Color = Color(0xFFF4F4F6),
    val info: Color = Color(0xFF05648E),
    /** Accent for TEXT on cards/pages (links, selected labels, tags): >= 4.5:1 */
    val accent: Color = Color(0xFF0646C8),
    /** Accent for ICONS and controls (switches, radios, progress, nav icon, checkmarks, selected border): >= 3:1 */
    val accentUi: Color = Color(0xFF0A59F7),
    /** Original vivid accent for pure decoration (backdrop gradient, glow, swatches); never for text */
    val accentFill: Color = Color(0xFF0A59F7),
    /** Text/icon color on the accent fill (= onPrimary) */
    val onAccent: Color = Color.White,
    /** Soft accent background (selection, highlighted chips) */
    val accentSoft: Color = Color(0x1C0A59F7),
    /** Upload / download colors for text (>= 4.5:1); up/down are for icons and charts */
    val upText: Color = Color(0xFF7C5CFF),
    val downText: Color = Color(0xFF16A6E8),
    val dark: Boolean = false,
    /** Opaque card color (menus, dialogs) */
    val solidCard: Color = Color.White,
)

typealias ExtraColors = Palette

val LocalPal = staticCompositionLocalOf { Palette() }
val LocalExtra get() = LocalPal

/**
 * One mode's accent tokens (checked by scripts/check-contrast.py).
 * ui: icons/controls (>= 3:1 on every surface), text: >= 4.5:1, fill: button fill, on: text on the fill (>= 4.5:1).
 */
data class AccentTone(val ui: Color, val text: Color, val fill: Color, val on: Color)

/** Preset accent colors; nameKey is an i18n key. light/dark are the original brand colors (swatches, decoration). Same as the HarmonyOS app. */
data class AccentDef(val key: String, val nameKey: String, val light: Color, val dark: Color, val lt: AccentTone, val dk: AccentTone)

// 1.2.1 (contrast check, same values as HarmonyOS 1.2.1): light presets that can't carry white text (orange / pink / green) keep
// their vivid fill with dark text; text/icon accents are darkened (light) or lightened (dark) just enough for WCAG.
val ACCENTS = listOf(
    AccentDef("blue", "accent_blue", Color(0xFF0A59F7), Color(0xFF3F7CFF),
        AccentTone(Color(0xFF0A59F7), Color(0xFF0646C8), Color(0xFF0A59F7), Color(0xFFFFFFFF)), AccentTone(Color(0xFF588DFF), Color(0xFF8EB2FF), Color(0xFF246AFF), Color(0xFFFFFFFF))),
    AccentDef("orange", "accent_orange", Color(0xFFF39A2B), Color(0xFFF7AE52),
        AccentTone(Color(0xFFB86B0A), Color(0xFF915408), Color(0xFFF39A2B), Color(0xFF1D2025)), AccentTone(Color(0xFFF7AE52), Color(0xFFFACB8E), Color(0xFFF7AE52), Color(0xFF15181C))),
    AccentDef("cat", "accent_cat", Color(0xFF3F5683), Color(0xFF7088B8),
        AccentTone(Color(0xFF3F5683), Color(0xFF3F5683), Color(0xFF3F5683), Color(0xFFFFFFFF)), AccentTone(Color(0xFF7D93BE), Color(0xFFA6B5D3), Color(0xFF5975AD), Color(0xFFFFFFFF))),
    AccentDef("red", "accent_red", Color(0xFFCF0A2C), Color(0xFFEE4A61),
        AccentTone(Color(0xFFCF0A2C), Color(0xFFA20723), Color(0xFFCF0A2C), Color(0xFFFFFFFF)), AccentTone(Color(0xFFF05B70), Color(0xFFF593A0), Color(0xFFE81633), Color(0xFFFFFFFF))),
    AccentDef("purple", "accent_purple", Color(0xFF8A5CD1), Color(0xFFA47FE0),
        AccentTone(Color(0xFF8A5CD1), Color(0xFF6C37BF), Color(0xFF8A5CD1), Color(0xFFFFFFFF)), AccentTone(Color(0xFFA885E1), Color(0xFFC5AEEB), Color(0xFF8B5CD7), Color(0xFFFFFFFF))),
    AccentDef("pink", "accent_pink", Color(0xFFFB7299), Color(0xFFFC8DAE),
        AccentTone(Color(0xFFEC0748), Color(0xFFB90538), Color(0xFFFB7299), Color(0xFF1D2025)), AccentTone(Color(0xFFFC8DAE), Color(0xFFFDBACE), Color(0xFFFC8DAE), Color(0xFF15181C))),
    AccentDef("green", "accent_green", Color(0xFF7BC23E), Color(0xFF93D15E),
        AccentTone(Color(0xFF598C2C), Color(0xFF456D22), Color(0xFF7BC23E), Color(0xFF1D2025)), AccentTone(Color(0xFF93D15E), Color(0xFFAFDD87), Color(0xFF93D15E), Color(0xFF15181C))),
)

fun findAccent(key: String): AccentDef = ACCENTS.firstOrNull { it.key == key } ?: ACCENTS[0]

private val lightBase = Palette()
private val darkBase = Palette(
    bg = Color(0xFF111316), card = Color(0xFF1C1F24), chip = Color(0xFF262A31), subtle = Color(0xFFA6ABB2),
    good = Color(0xFF52C487), warn = Color(0xFFF0B544), bad = Color(0xFFF48E8E), info = Color(0xFF38BDF8),
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
    val tone = if (dark) a.dk else a.lt
    var p = base.copy(
        accentFill = if (dark) a.dark else a.light, accent = tone.text, accentUi = tone.ui,
        accentSoft = tone.ui.copy(alpha = if (dark) 0.20f else 0.11f),
        primary = tone.fill, onPrimary = tone.on, onAccent = tone.on,
        // Traffic: download uses the accent, upload a shade of it (lighter on dark, deeper on light so it stays readable)
        down = tone.ui, up = if (dark) lerp(tone.ui, Color.White, 0.45f) else lerp(tone.ui, base.text, 0.45f),
        downText = tone.text, upText = if (dark) lerp(tone.text, Color.White, 0.45f) else lerp(tone.text, base.text, 0.45f),
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
    // 1.2.1: Material's primary is the TEXT accent (TextButton/OutlinedButton labels, text-field focus use it, so it must reach 4.5:1);
    // filled buttons use primaryButtonColors() (p.primary / p.onPrimary) and controls use accentUi via the *Colors() helpers below.
    val onText = if (p.dark) Color(0xFF15181C) else Color.White
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = onText, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
        secondary = Color(0xFF9AA4B2), background = Color(0xFF111316), surface = p.solidCard,
        surfaceVariant = Color(0xFF262A31), onSurface = p.text, onBackground = p.text, onSurfaceVariant = p.subtle,
        surfaceContainer = p.solidCard, surfaceContainerLow = p.solidCard, surfaceContainerHigh = Color(0xFF23272D),
        surfaceContainerHighest = Color(0xFF2A2F36), surfaceContainerLowest = Color(0xFF15181C),
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.text,
        outline = p.outline, outlineVariant = p.outlineVariant, error = p.bad,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = onText, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
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

/** Filled button: brand fill with its contrast-checked text color. */
@Composable
fun primaryButtonColors(): ButtonColors {
    val p = LocalPal.current
    return ButtonDefaults.buttonColors(containerColor = p.primary, contentColor = p.onPrimary)
}

/** Material 3 controls in the icon/control accent (>= 3:1). */
@Composable
fun accentSwitchColors(): SwitchColors {
    val p = LocalPal.current
    return SwitchDefaults.colors(checkedTrackColor = p.accentUi, checkedThumbColor = if (p.dark) Color(0xFF15181C) else Color.White, checkedIconColor = p.accentUi)
}

@Composable
fun accentRadioColors(): RadioButtonColors = RadioButtonDefaults.colors(selectedColor = LocalPal.current.accentUi)

/** Checkbox: accentUi box with a contrast-checked checkmark (>= 3:1 on the box). */
@Composable
fun accentCheckboxColors(): CheckboxColors {
    val p = LocalPal.current
    return CheckboxDefaults.colors(checkedColor = p.accentUi, checkmarkColor = if (p.dark) Color(0xFF15181C) else Color.White, uncheckedColor = p.subtle)
}

/** Slider: thumb and active track in accentUi, inactive track in chip. */
@Composable
fun accentSliderColors(): SliderColors {
    val p = LocalPal.current
    return SliderDefaults.colors(thumbColor = p.accentUi, activeTrackColor = p.accentUi, inactiveTrackColor = p.outline)
}
