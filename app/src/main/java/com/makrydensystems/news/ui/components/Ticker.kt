package com.makrydensystems.news.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.delay

/** Web `--redbright` (#EF4444): the ticker label dot. */
private val TickerRed = Color(0xFFEF4444)

/**
 * The LIVE ticker — the web's `.liveticker` under the header, native.
 * The app had none; the web cycles its live updates here.
 *
 * Data: the live items from `latest.json` — the SAME feed as the Live
 * now list below (via the repository's live-now flow), never invented.
 * One headline at a time, tappable to the story. Hidden entirely
 * when there is nothing live. Auto-advances every 8s like the web;
 * prev / pause / next re-arm the timer, also like the web. The label
 * reads "Breaking News" when the current item's label is "breaking",
 * otherwise "Live Updates".
 *
 * Layout follows the Mix: label row (dot + label + 48dp controls) on
 * top, the headline (14sp bold, 2 lines) below, on a deep band with
 * a 1px line border at the bottom.
 */
@Composable
fun LiveTicker(
    items: List<LiveItem>,
    onItemClick: (LiveItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by remember { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val currentIndex = index % items.size
    val current = items[currentIndex]

    // Auto-advance (web tickerArm: 8s). Restarts after every manual
    // step and every new list, and stops while paused or when there
    // is only one item to show.
    LaunchedEffect(items, currentIndex, paused) {
        if (!paused && items.size > 1) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StarBrookColors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TickerRed),
                    )
                    Text(
                        text = if (current.label == "breaking") "Breaking News"
                        else "Live Updates",
                        style = StarBrookType.label.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.9.sp,
                        ),
                        color = StarBrookColors.redsoft,
                        maxLines = 1,
                    )
                }
                TickerControlButton(
                    onClick = { index = (index - 1 + items.size) % items.size },
                    contentDescription = "Previous update",
                ) {
                    ChevronGlyph(pointLeft = true)
                }
                TickerControlButton(
                    onClick = { paused = !paused },
                    contentDescription = if (paused) "Resume live updates"
                    else "Pause live updates",
                ) {
                    if (paused) PlayGlyph() else PauseGlyph()
                }
                TickerControlButton(
                    onClick = { index = (index + 1) % items.size },
                    contentDescription = "Next update",
                ) {
                    ChevronGlyph(pointLeft = false)
                }
            }
            Text(
                text = current.title,
                style = StarBrookType.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = StarBrookColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(current) },
            )
        }
        HorizontalDivider(color = StarBrookColors.border, thickness = 1.dp)
    }
}

/**
 * One ticker control: a 48dp button, 10dp radius, 1px line border,
 * gold glyph. Press: ripple + 0.96 scale (shared [rememberPressScale]).
 */
@Composable
private fun TickerControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    glyph: @Composable BoxScope.() -> Unit,
) {
    val press = rememberPressScale()
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer(scaleX = press.scale, scaleY = press.scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .clickable(
                interactionSource = press.interactionSource,
                onClick = onClick,
            )
            .border(1.dp, StarBrookColors.border, RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/** A chevron, drawn — no icon-font dependency. */
@Composable
private fun ChevronGlyph(
    pointLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val gold = StarBrookColors.gold
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val path = Path().apply {
            if (pointLeft) {
                moveTo(size.width * 0.68f, size.height * 0.18f)
                lineTo(size.width * 0.32f, size.height * 0.5f)
                lineTo(size.width * 0.68f, size.height * 0.82f)
            } else {
                moveTo(size.width * 0.32f, size.height * 0.18f)
                lineTo(size.width * 0.68f, size.height * 0.5f)
                lineTo(size.width * 0.32f, size.height * 0.82f)
            }
        }
        drawPath(path, gold, style = stroke)
    }
}

/** Two pause bars, drawn. */
@Composable
private fun PauseGlyph(modifier: Modifier = Modifier) {
    val gold = StarBrookColors.gold
    Canvas(modifier = modifier.size(18.dp)) {
        val barWidth = 3.dp.toPx()
        val barHeight = size.height * 0.6f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        drawRoundRect(
            color = gold,
            topLeft = Offset(size.width * 0.28f, size.height * 0.2f),
            size = Size(barWidth, barHeight),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = gold,
            topLeft = Offset(size.width * 0.60f, size.height * 0.2f),
            size = Size(barWidth, barHeight),
            cornerRadius = radius,
        )
    }
}

/** A play triangle, drawn. */
@Composable
private fun PlayGlyph(modifier: Modifier = Modifier) {
    val gold = StarBrookColors.gold
    Canvas(modifier = modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.84f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            close()
        }
        drawPath(path, gold)
    }
}
