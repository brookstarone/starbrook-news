package com.makrydensystems.news.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makrydensystems.news.R

/** Web --green (#34D399): the LIVE dot. The theme palette deliberately
 * carries no green token (success states use gold); the live dot is
 * the one place the site's green is used, so it lives here. */
internal val StarBrookLiveGreen = Color(0xFF34D399)

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
 * The live green dot — the web's `.live-dot` (#34D399, 2s pulse).
 * Green + pulsing, never red/static: red is reserved for breaking
 * and alerts. Used in the masthead status row and Live now rows.
 * The core stays solid so the dot always reads; the ring is the pulse.
 */
@Composable
fun LiveDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    PulsingDot(
        color = StarBrookLiveGreen,
        durationMillis = 2000,
        size = size,
        modifier = modifier,
    )
}

/**
 * The breaking-strip dot — the web's `.breaking-kicker .dot`: white,
 * 8dp, 1.4s pulse. White on the red strip, never red-on-red (a red
 * dot on the red strip is invisible).
 */
@Composable
fun BreakingDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    PulsingDot(
        color = Color.White,
        durationMillis = 1400,
        size = size,
        modifier = modifier,
    )
}

/**
 * One pulsing dot, web-style: a ring blooms out from the solid core
 * and fades (the web's `box-shadow: 0 0 0 0 rgba(..., .55)` growing
 * to 7px and fading to transparent), then restarts. The ring is a
 * bordered circle scaled up, so it needs no canvas and no icon font.
 */
@Composable
private fun PulsingDot(
    color: Color,
    durationMillis: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "liveDotPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liveDotPulseProgress",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Expanding ring: grows ~2.2x while fading out. The Box does
        // not clip, so the ring blooms past the dot's bounds.
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val scale = 1f + progress * 1.2f
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.55f * (1f - progress)
                }
                .border(width = 1.5.dp, color = color, shape = CircleShape),
        )
        // Solid core — always fully opaque.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
        )
    }
}
