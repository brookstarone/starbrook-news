package com.makrydensystems.news.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun starBrookColorScheme(colors: StarBrookPalette) = darkColorScheme(
    primary = colors.gold,
    onPrimary = colors.onGold,
    secondary = colors.accentPurple,
    onSecondary = colors.textPrimary,
    tertiary = colors.liveRed,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.textSecondary,
    error = colors.liveRed,
    onError = colors.textPrimary,
    outline = colors.border,
    outlineVariant = colors.border,
)

private fun starBrookTypography() = Typography(
    displayLarge = StarBrookType.display,
    displayMedium = StarBrookType.display,
    displaySmall = StarBrookType.headline,
    headlineLarge = StarBrookType.headline,
    headlineMedium = StarBrookType.headline,
    headlineSmall = StarBrookType.title,
    titleLarge = StarBrookType.title,
    titleMedium = StarBrookType.title,
    titleSmall = StarBrookType.label,
    bodyLarge = StarBrookType.body,
    bodyMedium = StarBrookType.bodySmall,
    bodySmall = StarBrookType.caption,
    labelLarge = StarBrookType.label,
    labelMedium = StarBrookType.label,
    labelSmall = StarBrookType.caption,
)

/**
 * StarBrook theme. Wraps MaterialTheme with the StarBrook palette,
 * type scale, and shapes, and exposes [StarBrookColors] as a
 * CompositionLocal for direct token access.
 *
 * Dark-first and dark-only: there is no light theme. Every screen
 * renders on [StarBrookColors.background]; nothing may assume a
 * light surface.
 */
@Composable
fun StarBrookTheme(content: @Composable () -> Unit) {
    val colors = DarkStarBrookColors
    CompositionLocalProvider(LocalStarBrookColors provides colors) {
        MaterialTheme(
            colorScheme = starBrookColorScheme(colors),
            typography = starBrookTypography(),
            shapes = StarBrookMaterialShapes,
            content = content,
        )
    }
}

/** Convenience access to the current palette as a plain value. */
val StarBrookPaletteCurrent: StarBrookPalette
    @Composable get() = LocalStarBrookColors.current
