package com.makrydensystems.news.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * StarBrook palette — MATCHES THE LIVE SITE EXACTLY (Matthew's word
 * 2026-11-19 ~13:53 CST). Tokens are the live :root values from
 * https://makrydensystems.com/news/ (identical on the Weather page):
 * --night #0E0A1A, --deep #160F2B, --card #1B1233, --line #3A2E5C,
 * --gold #E8C266, --golddark #C9A227, --goldsoft #2A1F45, --text #F3EEFF,
 * --dim #A79DC4, --red #B91C1C, --reddeep #7F1D1D, --redsoft #FCA5A5,
 * --green #34D399.
 *
 * Mapping: [background] = --night, [surface] = --card, [surfaceVariant] =
 * --deep, [textPrimary] = --text, [textSecondary] = --dim, [gold] =
 * --gold, [liveRed] = --red, [border] = --line.
 *
 * Two deliberate exceptions: the Live Star logo artwork keeps its own red
 * dot (#EA252A, measured off the logo PNG) — only the UI red token follows
 * the site. [accentPurple] has no site equivalent and is retained for
 * CADE/link accents; [onGold] is functional (dark text on gold).
 * --green is NOT adopted as a token: success states use gold (one accent).
 */
@Immutable
data class StarBrookPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gold: Color,
    val goldsoft: Color,
    val golddark: Color,
    val liveRed: Color,
    val reddeep: Color,
    val redsoft: Color,
    val border: Color,
    val accentPurple: Color,
    val onGold: Color,
)

val DarkStarBrookColors = StarBrookPalette(
    background = Color(0xFF0E0A1A), // --night
    surface = Color(0xFF1B1233), // --card
    surfaceVariant = Color(0xFF160F2B), // --deep
    textPrimary = Color(0xFFF3EEFF), // --text
    textSecondary = Color(0xFFA79DC4), // --dim
    gold = Color(0xFFE8C266), // --gold
    goldsoft = Color(0xFF2A1F45), // --goldsoft
    golddark = Color(0xFFC9A227), // --golddark
    liveRed = Color(0xFFB91C1C), // --red
    reddeep = Color(0xFF7F1D1D), // --reddeep
    redsoft = Color(0xFFFCA5A5), // --redsoft
    border = Color(0xFF3A2E5C), // --line
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
    val goldsoft: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.goldsoft
    val golddark: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.golddark
    val liveRed: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.liveRed
    val reddeep: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.reddeep
    val redsoft: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.redsoft
    val border: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.border
    val accentPurple: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.accentPurple
    val onGold: Color
        @androidx.compose.runtime.Composable get() = LocalStarBrookColors.current.onGold
}
