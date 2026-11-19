package com.makrydensystems.news.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makrydensystems.news.R
import com.makrydensystems.news.ui.theme.StarBrookColors

/**
 * The Live Star logo, as a static vector (res/drawable/ic_live_star.xml).
 *
 * STATIC ONLY — never animated, never rotated, never recoloured. The
 * vector is a measurement-faithful trace of the canonical logo PNG
 * from the live News page masthead. It is the brand; it does not move.
 */
@Composable
fun LiveStarMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Image(
        painter = painterResource(R.drawable.ic_live_star),
        contentDescription = "StarBrook News",
        modifier = modifier.size(size),
    )
}

/**
 * The live red dot — the same red as the dot in the Live Star.
 * Used in Live now rows and the breaking strip. Static: the web
 * pulses its dot; the app keeps it still (calm, premium, no motion
 * for motion's sake).
 */
@Composable
fun LiveDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(StarBrookColors.liveRed),
    )
}
