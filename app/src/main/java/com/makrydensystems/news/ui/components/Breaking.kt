package com.makrydensystems.news.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * The breaking strip. It renders ONLY when [breaking] is non-null —
 * when nothing qualifies, the strip is gone entirely (no empty red
 * bar, no "no breaking news" text), exactly like the web: the strip
 * clears. Web `.breaking`: a full-width red band (#B91C1C) with a
 * 3px #7F1D1D bottom border, a WHITE pulsing 8dp dot (a red dot on
 * the red strip is invisible), "LIVE — BREAKING", the title, and
 * 13px #FCA5A5 meta (source · age · N outlets). One event = one
 * treatment. The strip sits inside the feed's 16dp edge padding, so
 * it is inset rather than edge-to-edge — the band styling is the
 * web's, the inset is the list's.
 */
@Composable
fun BreakingStrip(
    breaking: Breaking?,
    onClick: () -> Unit,
) {
    if (breaking == null) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RectangleShape,
            color = StarBrookColors.liveRed,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                BreakingDot(size = 8.dp, modifier = Modifier.padding(top = 5.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "LIVE — BREAKING",
                        style = StarBrookType.label.copy(letterSpacing = 1.4.sp),
                        color = StarBrookColors.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = breaking.title,
                        style = StarBrookType.title,
                        color = StarBrookColors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildList {
                        add(breaking.source)
                        if (!breaking.age.isNullOrBlank()) add(breaking.age)
                        if (breaking.outletCount > 1) add("${breaking.outletCount} outlets")
                    }.joinToString(" · ")
                    Text(
                        text = meta,
                        style = StarBrookType.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = StarBrookColors.redsoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(StarBrookColors.reddeep),
        )
    }
}

/**
 * One Live now row: green pulsing dot + headline + time, on a single
 * line. The Mix's compact vertical list — no horizontal swipe, no
 * wall. The headline takes the remaining width and ellipsizes; the
 * time never wraps and never gets pushed off the edge.
 */
@Composable
fun LiveNowRow(
    item: LiveItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot()
        Text(
            text = item.title,
            style = StarBrookType.bodySmall,
            color = StarBrookColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        val time = item.age?.takeIf { it.isNotBlank() }
            ?: formatRelativeTime(item.published)
        Text(
            text = time,
            style = StarBrookType.caption,
            color = StarBrookColors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * A section heading with an optional trailing action ("See all").
 * The action is a real gold-outline pill button with a real handler
 * — never decorative, never a plain-text link pretending to be a
 * button. When [onAction] is null, no action renders and the title
 * stands alone.
 */
@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = StarBrookType.headline,
            color = StarBrookColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                shape = StarBrookShapes.chip,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = StarBrookColors.gold,
                ),
                border = BorderStroke(1.dp, StarBrookColors.gold),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .height(48.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = StarBrookType.label,
                    maxLines = 1,
                )
            }
        }
    }
}
