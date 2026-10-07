package net.zash.clashpanel.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ExtraColors(
    val bg: Color,
    val card: Color,
    val chip: Color,
    val subtle: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val up: Color,
    val down: Color,
)

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(Color(0xFFF4F4F6), Color.White, Color(0xFFF1F2F4), Color(0xFF8A8F98), Color(0xFF3FA36B), Color(0xFFE0A030), Color(0xFFE05050), Color(0xFF7C5CFF), Color(0xFF16A6E8))
}

private val lightExtra = ExtraColors(
    bg = Color(0xFFF4F4F6), card = Color.White, chip = Color(0xFFF1F2F4), subtle = Color(0xFF8A8F98),
    good = Color(0xFF3FA36B), warn = Color(0xFFDB9A1F), bad = Color(0xFFE05050),
    up = Color(0xFF7C5CFF), down = Color(0xFF16A6E8),
)
private val darkExtra = ExtraColors(
    bg = Color(0xFF111316), card = Color(0xFF1C1F24), chip = Color(0xFF262A31), subtle = Color(0xFF8D939C),
    good = Color(0xFF52C487), warn = Color(0xFFF0B544), bad = Color(0xFFF06A6A),
    up = Color(0xFF9B85FF), down = Color(0xFF40BDF5),
)

@Composable
fun ClashTheme(theme: String, wallpaper: Boolean = false, cardAlpha: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFE6E8EB), onPrimary = Color(0xFF15181C),
        secondary = Color(0xFF9AA4B2), background = darkExtra.bg, surface = darkExtra.card,
        surfaceVariant = darkExtra.chip, onSurface = Color(0xFFE6E8EB), onBackground = Color(0xFFE6E8EB),
        surfaceContainer = darkExtra.card, surfaceContainerHigh = Color(0xFF23272D), surfaceContainerHighest = Color(0xFF2A2F36),
        secondaryContainer = Color(0xFF30353D), onSecondaryContainer = Color(0xFFE6E8EB),
        outline = Color(0xFF3A3F47), outlineVariant = Color(0xFF2C3037),
    ) else lightColorScheme(
        primary = Color(0xFF2B3036), onPrimary = Color.White,
        secondary = Color(0xFF5C6470), background = lightExtra.bg, surface = lightExtra.card,
        surfaceVariant = lightExtra.chip, onSurface = Color(0xFF1D2025), onBackground = Color(0xFF1D2025),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF7F7F9), surfaceContainerHighest = Color(0xFFEEEFF2),
        secondaryContainer = Color(0xFFE6E7EA), onSecondaryContainer = Color(0xFF1D2025),
        outline = Color(0xFFD5D8DD), outlineVariant = Color(0xFFE8EAED),
    )
    val base = if (dark) darkExtra else lightExtra
    val extra = if (wallpaper) base.copy(bg = Color.Transparent, card = base.card.copy(alpha = cardAlpha), chip = base.chip.copy(alpha = (cardAlpha + 0.1f).coerceAtMost(1f))) else base
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
