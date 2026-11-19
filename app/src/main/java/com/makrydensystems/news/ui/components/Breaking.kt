package com.makrydensystems.news.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * The breaking strip. It renders ONLY when [breaking] is non-null —
 * when nothing qualifies, the strip is gone entirely (no empty red
 * bar, no "no breaking news" text), exactly like the web: the strip
 * clears. Headline, source + age, and the outlet count ("3 outlets")
 * as the trust mark. One event = one treatment.
 */
@Composable
fun BreakingStrip(
    breaking: Breaking?,
    onClick: () -> Unit,
) {
    if (breaking == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = StarBrookShapes.card,
        color = StarBrookColors.liveRed,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            LiveDot(size = 9.dp, modifier = Modifier.padding(top = 5.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "BREAKING",
                    style = StarBrookType.label,
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
                    style = StarBrookType.caption,
                    color = StarBrookColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One Live now row: red dot + headline + time, on a single line.
 * The Mix's compact vertical list — no horizontal swipe, no wall.
 * The headline takes the remaining width and ellipsizes; the time
 * never wraps and never gets pushed off the edge.
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
 * The action is a real button with a real handler — never decorative.
 * When [onAction] is null, no action renders and the title stands alone.
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
            Text(
                text = actionLabel,
                style = StarBrookType.label,
                color = StarBrookColors.gold,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}
