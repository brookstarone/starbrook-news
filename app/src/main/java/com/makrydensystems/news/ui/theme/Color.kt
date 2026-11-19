package com.makrydensystems.news.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * StarBrook palette — dark-first, measured from the live web Mix
 * (https://makrydensystems.com/news/) and the canonical Live Star logo.
 *
 * - [background] is the Mix page background (#0A0714).
 * - [gold] is the contract accent (#E8C266). The logo's own gold measures
 *   #E7BC59 — visually identical; one gold is used everywhere.
 * - [liveRed] is the Live Star's red dot, measured from the logo PNG
 *   (#EA252A), not a generic Material red.
 * - Text is warm off-white, never pure white: pure white on near-black
 *   vibrates and feels cheap. Secondary text is a muted lavender-grey
 *   that stays readable at 12sp on [background].
 */
@Immutable
data class StarBrookPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gold: Color,
    val liveRed: Color,
    val border: Color,
    val accentPurple: Color,
    val onGold: Color,
)

val DarkStarBrookColors = StarBrookPalette(
    background = Color(0xFF0A0714),
    surface = Color(0xFF14101F),
    surfaceVariant = Color(0xFF1E1830),
    textPrimary = Color(0xFFF5F1E8),
    textSecondary = Color(0xFFA79FB8),
    gold = Color(0xFFE8C266),
    liveRed = Color(0xFFEA252A),
    border = Color(0xFF2A2338),
    accentPurple = Color(0xFF8B7CF6),
    onGold = Color(0xFF1A1206),
)

/**
 * StarBrook is dark-first by design. There is no light theme: the brand
 * is a night broadcast. Screens must never render a light surface.
 */
val LightStarBrookColors: StarBrookPalette = DarkStarBrookColors

val LocalStarBrookColors = staticCompositionLocalOf { DarkStarBrookColors }

/** The colors in effect inside [StarBrookTheme]. */
object StarBrookColors {
    val background: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.background
    val surface: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.surface
    val surfaceVariant: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.surfaceVariant
    val textPrimary: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.textPrimary
    val textSecondary: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.textSecondary
    val gold: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.gold
    val liveRed: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.liveRed
    val border: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.border
    val accentPurple: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.accentPurple
    val onGold: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.onGold
}
