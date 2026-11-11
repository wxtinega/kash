package com.kash.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Compact spacing scale. Screens use these, never ad-hoc numbers. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    /** Minimum touch target (Android accessibility guideline). */
    val touch = 48.dp
    /** Horizontal screen margin. */
    val screen = 16.dp
}

val KashShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/**
 * Light and dark are designed as separate palettes (not inverted copies). Follows the system
 * setting by default; the user can force one in Settings. Android's wallpaper-based "dynamic
 * colour" is deliberately off so the brand accent is always #F86939.
 */
@Composable
fun KashTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val k = if (dark) DarkKashColors else LightKashColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = k.accent, onPrimary = k.onAccent,
            background = k.background, onBackground = k.text,
            surface = k.background, onSurface = k.text,
            surfaceVariant = k.glassStrong, onSurfaceVariant = k.textMuted,
            outline = k.border, outlineVariant = k.border,
            error = k.negative,
        )
    } else {
        lightColorScheme(
            primary = k.accent, onPrimary = k.onAccent,
            background = k.background, onBackground = k.text,
            surface = k.background, onSurface = k.text,
            surfaceVariant = k.glassStrong, onSurfaceVariant = k.textMuted,
            outline = k.border, outlineVariant = k.border,
            error = k.negative,
        )
    }
    CompositionLocalProvider(LocalKashColors provides k) {
        MaterialTheme(colorScheme = scheme, typography = KashTypography, shapes = KashShapes, content = content)
    }
}

/** Access: `Kash.colors.accent` */
object Kash {
    val colors: KashColors
        @Composable @ReadOnlyComposable get() = LocalKashColors.current
}
