package com.makrydensystems.news.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * Shimmer. One animation, shared by every skeleton in the app:
 * a soft diagonal sheen sweeping across the card, 1.1s, endless.
 * No spinners anywhere in the app — the instant standard means a
 * cached feed shows instantly and a cold start shows skeletons
 * shaped like the content coming, never a blank screen.
 */
private fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val base = StarBrookColors.surface
    val sheen = StarBrookColors.surfaceVariant
    background(
        Brush.linearGradient(
            colors = listOf(base, sheen, base),
            start = Offset(offset * 300f, 0f),
            end = Offset(offset * 300f + 300f, 300f),
        ),
    )
}

/** A skeleton shaped like a [NewsCard]: text block + thumbnail. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .shimmer(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(16.dp)
                    .shimmer(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .shimmer(),
            )
        }
    }
}

/** [count] skeleton cards, spaced like the real list. */
@Composable
fun SkeletonList(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count.coerceIn(1, 10)) {
            SkeletonCard()
        }
    }
}

/**
 * Empty state — a real outcome, not a failure. "You're caught up",
 * "Nothing saved yet", "No video for this ZIP yet". Always a title,
 * always a body that says what to do next, optionally an action that
 * does it. The Live Star marks the spot so the screen never feels bare.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveStarMark(size = 56.dp)
        Text(
            text = title,
            style = StarBrookType.title,
            color = StarBrookColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StarBrookColors.gold,
                    contentColor = StarBrookColors.onGold,
                ),
                shape = StarBrookShapes.chip,
            ) {
                Text(text = actionLabel, style = StarBrookType.label)
            }
        }
    }
}

/**
 * Error state — plain language, no codes, one way out: Retry.
 * Network failures, stale caches, and empty editions all land here
 * with a message the reader can act on. Never a raw exception, never
 * a dead end.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(StarBrookColors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = StarBrookType.display,
                color = StarBrookColors.liveRed,
            )
        }
        Text(
            text = "Something went wrong",
            style = StarBrookType.title,
            color = StarBrookColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = StarBrookColors.gold,
                contentColor = StarBrookColors.onGold,
            ),
            shape = StarBrookShapes.chip,
        ) {
            Text(text = "Try again", style = StarBrookType.label)
        }
    }
}
