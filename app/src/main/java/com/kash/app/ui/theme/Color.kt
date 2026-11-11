package com.kash.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Kash palette. One accent (#F86939), warm neutrals, no gradients.
 * Every text colour is checked for at least 4.5:1 contrast against its background:
 *  - text ON the orange is near-black (white on #F86939 is only 2.97:1);
 *  - orange used AS text on light backgrounds is the deeper #B9461F (5.3:1).
 * Green and red are used only for small amount indicators, never for large areas.
 */
object Palette {
    val Accent = Color(0xFFF86939)
    val AccentInk = Color(0xFF1C1410)          // text/icons on accent (6.1:1)
    val AccentTextLight = Color(0xFFB9461F)     // accent-coloured text on light surfaces

    // Light
    val LightBackground = Color(0xFFF6F5F3)
    val LightText = Color(0xFF17171A)
    val LightTextMuted = Color(0xFF5E5E66)
    val LightGlass = Color(0xB8FFFFFF)          // 72% white
    val LightGlassStrong = Color(0xE6FFFFFF)
    val LightBorder = Color(0x14000000)         // 8% black
    val LightPositive = Color(0xFF1A7F55)
    val LightNegative = Color(0xFFC2362F)

    // Dark
    val DarkBackground = Color(0xFF0E0E10)
    val DarkText = Color(0xFFF2F2F3)
    val DarkTextMuted = Color(0xFFA1A1AA)
    val DarkGlass = Color(0x0FFFFFFF)           // 6% white
    val DarkGlassStrong = Color(0x1AFFFFFF)
    val DarkBorder = Color(0x1AFFFFFF)          // 10% white
    val DarkPositive = Color(0xFF4ADE9A)
    val DarkNegative = Color(0xFFF87171)

    val Warning = Color(0xFFE8A13A)
}

/** Colours Material doesn't model: glass layers, borders, money indicators. */
@Immutable
data class KashColors(
    val background: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val glass: Color,
    val glassStrong: Color,
    val border: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    /** Faint accent wash behind the top of screens: gives the glass something to sit on. */
    val backdropTint: Color,
    val skeleton: Color,
    val isDark: Boolean,
)

val LightKashColors = KashColors(
    background = Palette.LightBackground,
    text = Palette.LightText,
    textMuted = Palette.LightTextMuted,
    accent = Palette.Accent,
    onAccent = Palette.AccentInk,
    accentText = Palette.AccentTextLight,
    glass = Palette.LightGlass,
    glassStrong = Palette.LightGlassStrong,
    border = Palette.LightBorder,
    positive = Palette.LightPositive,
    negative = Palette.LightNegative,
    warning = Palette.Warning,
    backdropTint = Palette.Accent.copy(alpha = 0.10f),
    skeleton = Color(0x0F000000),
    isDark = false,
)

val DarkKashColors = KashColors(
    background = Palette.DarkBackground,
    text = Palette.DarkText,
    textMuted = Palette.DarkTextMuted,
    accent = Palette.Accent,
    onAccent = Palette.AccentInk,
    accentText = Palette.Accent,
    glass = Palette.DarkGlass,
    glassStrong = Palette.DarkGlassStrong,
    border = Palette.DarkBorder,
    positive = Palette.DarkPositive,
    negative = Palette.DarkNegative,
    warning = Palette.Warning,
    backdropTint = Palette.Accent.copy(alpha = 0.12f),
    skeleton = Color(0x14FFFFFF),
    isDark = true,
)

val LocalKashColors = staticCompositionLocalOf { LightKashColors }
