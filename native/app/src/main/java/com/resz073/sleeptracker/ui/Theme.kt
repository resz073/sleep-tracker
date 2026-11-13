package com.resz073.sleeptracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.resz073.sleeptracker.R

/** Colours from the web app (src/styles.css), light and dark. */
@Immutable
data class AppColors(
    val bg: Color, val card: Color, val card2: Color, val ink: Color, val mute: Color, val line: Color,
    val acc: Color, val accSoft: Color, val onAcc: Color, val good: Color, val bad: Color, val warn: Color,
    val sky: Color, val skyInk: Color, val skyMute: Color, val skyAcc: Color, val skyLine: Color,
)

private val Light = AppColors(
    bg = Color(0xFFF4F3FB), card = Color(0xFFFFFFFF), card2 = Color(0xFFEBEAF7), ink = Color(0xFF1A1E45),
    mute = Color(0xFF5D6296), line = Color(0xFFDDDCEF), acc = Color(0xFF4D52CF), accSoft = Color(0xFFE3E3FB),
    onAcc = Color(0xFFFFFFFF), good = Color(0xFF137A56), bad = Color(0xFFC03A45), warn = Color(0xFF8F5F00),
    sky = Color(0xFF141C48), skyInk = Color(0xFFEEF0FF), skyMute = Color(0xFFAAB0E0), skyAcc = Color(0xFFA3ABFF),
    skyLine = Color(0xFF2C3679),
)

private val Dark = AppColors(
    bg = Color(0xFF10163A), card = Color(0xFF182052), card2 = Color(0xFF1F2960), ink = Color(0xFFEEF0FF),
    mute = Color(0xFFA3A9D6), line = Color(0xFF2A336C), acc = Color(0xFFA3ABFF), accSoft = Color(0xFF2B3579),
    onAcc = Color(0xFF10163A), good = Color(0xFF45D6A6), bad = Color(0xFFFF8A8A), warn = Color(0xFFEFCB68),
    sky = Color(0xFF0A0E2B), skyInk = Color(0xFFEEF0FF), skyMute = Color(0xFFAAB0E0), skyAcc = Color(0xFFA3ABFF),
    skyLine = Color(0xFF232B6A),
)

val LocalAppColors = staticCompositionLocalOf { Light }

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private fun nunitoTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        displayMedium = t.displayMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        displaySmall = t.displaySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.copy(fontFamily = Nunito),
        bodyMedium = t.bodyMedium.copy(fontFamily = Nunito),
        bodySmall = t.bodySmall.copy(fontFamily = Nunito),
        labelLarge = t.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        labelMedium = t.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        labelSmall = t.labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun SleepTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) Dark else Light
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = c.acc, onPrimary = c.onAcc, primaryContainer = c.accSoft, onPrimaryContainer = c.ink,
            secondary = c.acc, onSecondary = c.onAcc, secondaryContainer = c.accSoft, onSecondaryContainer = c.ink,
            background = c.bg, onBackground = c.ink, surface = c.card, onSurface = c.ink,
            surfaceVariant = c.card2, onSurfaceVariant = c.mute, outline = c.line, outlineVariant = c.line,
            error = c.bad, onError = Color(0xFF2A0A0D),
            surfaceContainerLowest = c.card, surfaceContainerLow = c.card, surfaceContainer = c.card,
            surfaceContainerHigh = c.card2, surfaceContainerHighest = c.card2,
        )
    } else {
        lightColorScheme(
            primary = c.acc, onPrimary = c.onAcc, primaryContainer = c.accSoft, onPrimaryContainer = c.ink,
            secondary = c.acc, onSecondary = c.onAcc, secondaryContainer = c.accSoft, onSecondaryContainer = c.ink,
            background = c.bg, onBackground = c.ink, surface = c.card, onSurface = c.ink,
            surfaceVariant = c.card2, onSurfaceVariant = c.mute, outline = c.line, outlineVariant = c.line,
            error = c.bad, onError = Color.White,
            surfaceContainerLowest = c.card, surfaceContainerLow = c.card, surfaceContainer = c.card,
            surfaceContainerHigh = c.card2, surfaceContainerHighest = c.card2,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = nunitoTypography(), content = content)
    }
}
