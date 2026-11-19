package com.makrydensystems.news.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.makrydensystems.news.R

/**
 * StarBrook type — two families, locked 2026-11-12.
 *
 * Display / headlines: Liberation Sans Bold. It is the web wordmark's
 * voice — blunt, confident, broadcast. Body / UI: Inter, the most
 * legible screen sans at small sizes.
 *
 * Fonts are bundled in res/font (downloaded at build time by
 * gradle/font-download.gradle — never committed as binaries).
 * If a font file is ever missing, the system fallback still renders;
 * nothing here may crash on a missing font.
 */
val LiberationSansBold = FontFamily(
    Font(R.font.liberation_sans_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * The StarBrook type scale. Line heights are generous on purpose:
 * headlines breathe, body copy never feels cramped on a phone.
 * Long headlines are the norm in news — every style here is used with
 * maxLines + ellipsis in components, never with fixed heights.
 */
object StarBrookType {
    /** Hero / featured lead. Matches the Mix featured lead (27px mobile). */
    val display = TextStyle(
        fontFamily = LiberationSansBold,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
    )

    /** Card headlines, section leads. */
    val headline = TextStyle(
        fontFamily = LiberationSansBold,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
    )

    /** Smaller headlines, read-view subheads, dialog titles. */
    val title = TextStyle(
        fontFamily = LiberationSansBold,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    )

    /** Article excerpts, read-view paragraphs, chat messages. */
    val body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** Secondary copy, card excerpts, list subtitles. */
    val bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** Source lines, timestamps, meta, footnotes. */
    val caption = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** Buttons, chips, eyebrows, tab labels. Tracked slightly open. */
    val label = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
}
