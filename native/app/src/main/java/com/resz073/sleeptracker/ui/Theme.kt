@file:OptIn(ExperimentalTextApi::class)

package com.resz073.sleeptracker.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.resz073.sleeptracker.R
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The colours every screen uses. In dark mode they're Lamplight's: warm and dim, with no blue in them.
 * In light mode they come from the phone's wallpaper (Android 12 and later), or a sage green before that.
 */
@Immutable
data class AppColors(
    val dark: Boolean,
    /** The screen */
    val bg: Color,
    /** The sheet under Today's and Schedule's top part, and the bottom bar */
    val panel: Color,
    /** Fields, chips and quieter buttons */
    val raised: Color,
    /** The chosen option of a segmented control */
    val sel: Color, val onSel: Color,
    /** Dividers and outlines */
    val line: Color,
    val ink: Color,
    /** Quieter text */
    val mute: Color,
    /** Amber in dark mode, the wallpaper's colour in light mode */
    val acc: Color, val onAcc: Color,
    /** The main buttons (Going to sleep, Save) */
    val tonal: Color, val onTonal: Color,
    val bad: Color, val onBad: Color,
    /** Not synced yet, nights not logged */
    val warn: Color,
    /** Trends: days that met the goal, and days under it */
    val met: Color, val under: Color,
    /** The goal line */
    val goal: Color,
    /** Schedule: the planned sleep window */
    val window: Color,
    val switchOn: Color, val switchOnThumb: Color,
    val switchOff: Color, val switchOffThumb: Color, val switchOffBorder: Color,
    /** The Add sleep / Edit sleep sheet */
    val sheet: Color,
)

private val Lamplight = AppColors(
    dark = true,
    bg = Color(0xFF151011), panel = Color(0xFF1F1918), raised = Color(0xFF2B2220),
    sel = Color(0xFF362B27), onSel = Color(0xFFE2D1BA), line = Color(0xFF3A2F2C),
    ink = Color(0xFFE2D1BA), mute = Color(0xFFA8937F),
    acc = Color(0xFFCF9552), onAcc = Color(0xFF151011),
    tonal = Color(0xFF4A3420), onTonal = Color(0xFFF0C892),
    bad = Color(0xFFE58C7E), onBad = Color(0xFF2A0F0B),
    warn = Color(0xFFCF9552),
    met = Color(0xFFBE803A), under = Color(0xFF9C4E4D), goal = Color(0xFF8C7865),
    window = Color(0xFFCF9552).copy(alpha = 0.10f),
    switchOn = Color(0xFF4A3420), switchOnThumb = Color(0xFFF0C892),
    switchOff = Color(0xFF2B2220), switchOffThumb = Color(0xFF8C7865), switchOffBorder = Color(0xFF5A4A44),
    sheet = Color(0xFF1F1918),
)

/** Material's own parts (menus, dialogs, the message bar) in Lamplight's colours */
private val LamplightScheme = darkColorScheme(
    primary = Lamplight.acc, onPrimary = Lamplight.onAcc,
    primaryContainer = Lamplight.tonal, onPrimaryContainer = Lamplight.onTonal,
    inversePrimary = Color(0xFF6B4A22),
    secondary = Lamplight.acc, onSecondary = Lamplight.onAcc,
    secondaryContainer = Lamplight.sel, onSecondaryContainer = Lamplight.onSel,
    tertiary = Lamplight.acc, onTertiary = Lamplight.onAcc,
    tertiaryContainer = Lamplight.tonal, onTertiaryContainer = Lamplight.onTonal,
    background = Lamplight.bg, onBackground = Lamplight.ink,
    surface = Lamplight.bg, onSurface = Lamplight.ink,
    surfaceVariant = Lamplight.raised, onSurfaceVariant = Lamplight.mute,
    surfaceTint = Color.Transparent,
    inverseSurface = Lamplight.ink, inverseOnSurface = Lamplight.bg,
    error = Lamplight.bad, onError = Lamplight.onBad,
    outline = Lamplight.goal, outlineVariant = Lamplight.line,
    surfaceContainerLowest = Lamplight.bg, surfaceContainerLow = Lamplight.panel,
    surfaceContainer = Lamplight.panel, surfaceContainerHigh = Lamplight.raised,
    surfaceContainerHighest = Lamplight.sel,
)

/** Before Android 12 there are no wallpaper colours: a sage green, as Android would make from a green wallpaper */
private val SageScheme = lightColorScheme(
    primary = Color(0xFF4C662B), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDA3), onPrimaryContainer = Color(0xFF354E16),
    inversePrimary = Color(0xFFB1D18A),
    secondary = Color(0xFF586249), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7C8), onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7), onTertiaryContainer = Color(0xFF1F4E4B),
    background = Color(0xFFF9FAEF), onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF), onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5), onSurfaceVariant = Color(0xFF44483D),
    inverseSurface = Color(0xFF2F312A), inverseOnSurface = Color(0xFFF1F2E6),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF75796C), outlineVariant = Color(0xFFC5C8BA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F4E9),
    surfaceContainer = Color(0xFFEEEFE3), surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE2E3D8),
)

/** The app's colours from a light Material scheme (the wallpaper's, or sage) */
private fun fromScheme(s: ColorScheme): AppColors {
    val (met, under) = chartColors(s.primary)
    return AppColors(
        dark = false,
        bg = s.surface, panel = s.surfaceContainer, raised = s.surfaceContainerHigh,
        sel = s.secondaryContainer, onSel = s.onSecondaryContainer, line = s.outlineVariant,
        ink = s.onSurface, mute = s.onSurfaceVariant,
        acc = s.primary, onAcc = s.onPrimary,
        tonal = s.primaryContainer, onTonal = s.onPrimaryContainer,
        bad = s.error, onBad = s.onError,
        warn = Color(0xFF8F5F00),
        met = met, under = under, goal = s.outline,
        window = s.primary.copy(alpha = 0.13f),
        switchOn = s.primary, switchOnThumb = s.onPrimary,
        switchOff = s.surfaceContainerHighest, switchOffThumb = s.outline, switchOffBorder = s.outline,
        sheet = s.surfaceContainerLow,
    )
}

/**
 * Two chart colours made from the accent: a darker one for days that met the goal and a lighter one for days
 * under it (OKLCH lightness 0.48 and 0.645). Told apart by lightness, so they work for colour-blind eyes too.
 */
private fun chartColors(accent: Color): Pair<Color, Color> {
    val (_, c, h) = oklch(accent)
    return fromOklch(0.48, maxOf(c, 0.105), h) to fromOklch(0.645, 0.10, h)
}

private fun linear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
private fun gamma(v: Double): Float {
    val x = v.coerceIn(0.0, 1.0)
    return (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).toFloat()
}

private fun oklch(color: Color): Triple<Double, Double, Double> {
    val r = linear(color.red)
    val g = linear(color.green)
    val b = linear(color.blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return Triple(lightness, hypot(a, bb), Math.toDegrees(atan2(bb, a)))
}

private fun fromOklch(lightness: Double, chroma: Double, hue: Double): Color {
    val a = chroma * cos(Math.toRadians(hue))
    val b = chroma * sin(Math.toRadians(hue))
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return Color(
        gamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        gamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        gamma(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    )
}

val LocalAppColors = staticCompositionLocalOf { Lamplight }

/** Literata, made for reading on screens: times, figures and headings. Big times use its display optical size. */
val Literata = FontFamily(
    Font(R.font.literata, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300), FontVariation.Setting("opsz", 72f))),
    Font(R.font.literata, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.Setting("opsz", 28f))),
    Font(R.font.literata, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500), FontVariation.Setting("opsz", 28f))),
)

/** Atkinson Hyperlegible Next, easy to read when tired: everything else */
val Atkinson = FontFamily(
    Font(R.font.atkinson_next, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.atkinson_next, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.atkinson_next, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun appTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Literata),
        displayMedium = t.displayMedium.copy(fontFamily = Literata),
        displaySmall = t.displaySmall.copy(fontFamily = Literata),
        headlineLarge = t.headlineLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        headlineMedium = t.headlineMedium.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        headlineSmall = t.headlineSmall.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        titleLarge = t.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        titleMedium = t.titleMedium.copy(fontFamily = Atkinson, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = Atkinson, fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.copy(fontFamily = Atkinson),
        bodyMedium = t.bodyMedium.copy(fontFamily = Atkinson),
        bodySmall = t.bodySmall.copy(fontFamily = Atkinson),
        labelLarge = t.labelLarge.copy(fontFamily = Atkinson, fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.copy(fontFamily = Atkinson, fontWeight = FontWeight.Bold),
        labelSmall = t.labelSmall.copy(fontFamily = Atkinson, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun SleepTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        dark -> LamplightScheme
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(LocalContext.current)
        else -> SageScheme
    }
    val colors = if (dark) Lamplight else fromScheme(scheme)
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(), content = content)
    }
}
