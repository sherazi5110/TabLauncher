@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.tablauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tablauncher.R
import com.tablauncher.data.AppFont
import com.tablauncher.data.ClockWeight

@Immutable
data class LauncherColors(
    val dark: Boolean,
    val text: Color,
    val text2: Color,
    val card: Color,
    val card2: Color,
    val line: Color,
    val page: Color,
    val accent: Color,
    val folderBg: Color,
    /** Text drawn straight on the wallpaper (clock, icon labels). */
    val onWallpaper: Color,
    val onWallpaperShadow: Color,
)

fun launcherColors(dark: Boolean, wallpaperIsDark: Boolean): LauncherColors {
    val onWp = if (wallpaperIsDark) Color.White else Color(0xFF1C1C1E)
    val shadow = if (wallpaperIsDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.0f)
    return if (dark) LauncherColors(
        dark = true, text = Color(0xFFF5F5F7), text2 = Color(0xFF9A9AA2), card = Color(0xFF1C1C1E),
        card2 = Color(0xFF2A2A2D), line = Color(0xFF2E2E32), page = Color(0xFF0B0B0D), accent = Color(0xFF5B8CFF),
        folderBg = Color(0xFF2A2A2E), onWallpaper = onWp, onWallpaperShadow = shadow,
    ) else LauncherColors(
        dark = false, text = Color(0xFF1C1C1E), text2 = Color(0xFF6B6B72), card = Color.White,
        card2 = Color(0xFFF2F2F5), line = Color(0xFFE6E6EA), page = Color(0xFFF2F2F7), accent = Color(0xFF2F6BFF),
        folderBg = Color(0xFFE4E6EC), onWallpaper = onWp, onWallpaperShadow = shadow,
    )
}

val LocalColors = staticCompositionLocalOf { launcherColors(false, false) }
val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalClockWeight = staticCompositionLocalOf { FontWeight.Light }

private fun variable(res: Int): FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private val families = mutableMapOf<AppFont, FontFamily>()

fun fontFamily(font: AppFont): FontFamily = families.getOrPut(font) {
    when (font) {
        AppFont.SYSTEM -> FontFamily.Default
        AppFont.INTER -> variable(R.font.inter)
        AppFont.NUNITO -> variable(R.font.nunito)
        AppFont.OUTFIT -> variable(R.font.outfit)
        AppFont.MANROPE -> variable(R.font.manrope)
        AppFont.QUICKSAND -> variable(R.font.quicksand)
        AppFont.LORA -> variable(R.font.lora)
        AppFont.POPPINS -> FontFamily(
            Font(R.font.poppins_light, FontWeight.Light),
            Font(R.font.poppins_regular, FontWeight.Normal),
            Font(R.font.poppins_medium, FontWeight.Medium),
            Font(R.font.poppins_semibold, FontWeight.SemiBold),
            Font(R.font.poppins_semibold, FontWeight.Bold),
        )
    }
}

fun clockWeight(w: ClockWeight) = when (w) {
    ClockWeight.THIN -> FontWeight.ExtraLight
    ClockWeight.LIGHT -> FontWeight.Light
    ClockWeight.REGULAR -> FontWeight.Normal
    ClockWeight.BOLD -> FontWeight.SemiBold
}

@Composable
fun LauncherTheme(colors: LauncherColors, font: FontFamily, clock: FontWeight, content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = font)
    val typography = Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
    val scheme = if (colors.dark) darkColorScheme(
        primary = colors.accent, surface = colors.card, background = colors.page, onSurface = colors.text,
        surfaceContainer = colors.card, surfaceContainerHigh = colors.card2, surfaceContainerLow = colors.card,
    ) else lightColorScheme(
        primary = colors.accent, surface = colors.card, background = colors.page, onSurface = colors.text,
        surfaceContainer = colors.card, surfaceContainerHigh = colors.card2, surfaceContainerLow = colors.card,
    )
    CompositionLocalProvider(LocalColors provides colors, LocalFontFamily provides font, LocalClockWeight provides clock) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
