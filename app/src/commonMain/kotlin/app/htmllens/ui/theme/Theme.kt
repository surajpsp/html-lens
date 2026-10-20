package app.htmllens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.htmllens.resources.Res
import app.htmllens.resources.newsreader
import app.htmllens.resources.plex_mono
import app.htmllens.resources.plex_mono_medium
import app.htmllens.resources.plex_sans
import org.jetbrains.compose.resources.Font

/**
 * "Warm paper" palette: paper grounds, ink text, one terracotta accent.
 * Field names are semantic so screens never hard-code colors.
 */
@Immutable
data class LensColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    /** Terracotta accent: links, focus, selection. */
    val primary: Color,
    val primaryHover: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    /** Ink: the main call-to-action fill. */
    val ink: Color,
    val onInk: Color,
    val accent: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val code: Color,
    val scrim: Color,
)

val LightLens = LensColors(
    isDark = false,
    bg = Color(0xFFF6F1E7),
    surface = Color(0xFFFBF8F1),
    surface2 = Color(0xFFF0E9DB),
    surface3 = Color(0xFFE7DECB),
    border = Color(0xFFE2D9C6),
    borderStrong = Color(0xFFCFC4AE),
    text = Color(0xFF1E1A15),
    textMuted = Color(0xFF574F42),
    textFaint = Color(0xFF857C6C),
    primary = Color(0xFFA8432A),
    primaryHover = Color(0xFF8C3420),
    primarySoft = Color(0xFFF3E0D6),
    onPrimary = Color(0xFFFFFFFF),
    ink = Color(0xFF1E1A15),
    onInk = Color(0xFFF6F1E7),
    accent = Color(0xFF8A6A3E),
    success = Color(0xFF3D6A38),
    successSoft = Color(0xFFE1EAD7),
    warning = Color(0xFF94580B),
    warningSoft = Color(0xFFF5E5C8),
    danger = Color(0xFF9E2A1F),
    dangerSoft = Color(0xFFF4DCD5),
    info = Color(0xFF2F5A86),
    infoSoft = Color(0xFFDDE6EE),
    code = Color(0xFFFBF8F1),
    scrim = Color(0x661E1A15),
)

val DarkLens = LensColors(
    isDark = true,
    bg = Color(0xFF1B1915),
    surface = Color(0xFF211E1A),
    surface2 = Color(0xFF2A2621),
    surface3 = Color(0xFF332E27),
    border = Color(0xFF36302A),
    borderStrong = Color(0xFF4A4339),
    text = Color(0xFFEEE7DA),
    textMuted = Color(0xFFB8AE9C),
    textFaint = Color(0xFF8F8574),
    primary = Color(0xFFE58A63),
    primaryHover = Color(0xFFF2A585),
    primarySoft = Color(0xFF3A2A22),
    onPrimary = Color(0xFF1B1915),
    ink = Color(0xFFEEE7DA),
    onInk = Color(0xFF1B1915),
    accent = Color(0xFFCFAE7C),
    success = Color(0xFF9CC99A),
    successSoft = Color(0xFF243121),
    warning = Color(0xFFE3B261),
    warningSoft = Color(0xFF362B18),
    danger = Color(0xFFF08A74),
    dangerSoft = Color(0xFF3A1F1A),
    info = Color(0xFF93B6DB),
    infoSoft = Color(0xFF1E2733),
    code = Color(0xFF1F1C18),
    scrim = Color(0x99000000),
)

val LocalLens = staticCompositionLocalOf { LightLens }
val LocalMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
val LocalSerif = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

object Lens {
    val colors: LensColors @Composable get() = LocalLens.current
    val mono: FontFamily @Composable get() = LocalMono.current
    val serif: FontFamily @Composable get() = LocalSerif.current
}

/** Muted, earthy tones for saved-app tiles. */
val AppTileColors = listOf(
    Color(0xFFA8432A), Color(0xFF3D6A38), Color(0xFF2F5A86), Color(0xFF8A6A3E),
    Color(0xFF6B4E7A), Color(0xFF2D6B6B), Color(0xFFB0702A), Color(0xFF7A3B4E),
    Color(0xFF4B5563), Color(0xFF1E1A15),
)

@Composable
fun HtmlLensTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkLens else LightLens
    val sans = FontFamily(
        Font(Res.font.plex_sans, FontWeight.Normal),
        Font(Res.font.plex_sans, FontWeight.Medium),
        Font(Res.font.plex_sans, FontWeight.SemiBold),
        Font(Res.font.plex_sans, FontWeight.Bold),
    )
    val serif = FontFamily(
        Font(Res.font.newsreader, FontWeight.Normal),
        Font(Res.font.newsreader, FontWeight.Medium),
        Font(Res.font.newsreader, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(Res.font.plex_mono, FontWeight.Normal),
        Font(Res.font.plex_mono_medium, FontWeight.Medium),
    )
    val s = TextStyle(fontFamily = sans, color = c.text)
    val d = TextStyle(fontFamily = serif, color = c.text)
    val typography = Typography(
        displayLarge = d.copy(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
        displayMedium = d.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.8).sp),
        displaySmall = d.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        headlineMedium = d.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
        headlineSmall = d.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
        titleLarge = d.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleMedium = s.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = s.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = s.copy(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = s.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = s.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = s.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelMedium = s.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
        labelSmall = s.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
    val scheme = if (dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primarySoft, onPrimaryContainer = c.text,
        secondary = c.accent, background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.textMuted, outline = c.borderStrong, outlineVariant = c.border,
        error = c.danger, surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3,
        surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg, inverseSurface = c.text, inverseOnSurface = c.bg,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primarySoft, onPrimaryContainer = c.text,
        secondary = c.accent, background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.textMuted, outline = c.borderStrong, outlineVariant = c.border,
        error = c.danger, surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3,
        surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg, inverseSurface = c.text, inverseOnSurface = c.bg,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(14.dp),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalLens provides c, LocalMono provides mono, LocalSerif provides serif, content = content)
    }
}
