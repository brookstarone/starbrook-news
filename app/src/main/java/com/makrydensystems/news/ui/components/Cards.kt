package com.makrydensystems.news.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makrydensystems.news.data.model.BriefingVideo
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * Image with a guaranteed fallback. A story image may be null, may 404,
 * or may be a tracking pixel — in every case the card shows the Live
 * Star on a surface, never a blank hole and never a broken-image glyph.
 */
@Composable
private fun StoryImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(StarBrookColors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // The Live Star is painted FIRST, underneath: it is the
        // fallback, visible while loading and on error. The image
        // paints over it when (and only when) it loads.
        LiveStarMark(size = 40.dp)
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * The gold "StarBrook Original" pill. Originals are the newsroom's own
 * reporting — the badge is the reader's trust mark, so it is always
 * gold on dark, always the same words, never restyled per screen.
 */
@Composable
fun GoldOriginalBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = StarBrookShapes.chip,
        color = StarBrookColors.gold,
    ) {
        Text(
            text = "StarBrook Original",
            style = StarBrookType.label,
            color = StarBrookColors.onGold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Card chrome shared by the rebuilt cards (web .card / .pill / .trust).
// ---------------------------------------------------------------------------

/** Web `--r-lg` (14px): the site's card radius. */
private val NewsCardShape = RoundedCornerShape(14.dp)

/** Web card-image radius (10px): images sit inside the card padding. */
private val NewsCardImageShape = RoundedCornerShape(10.dp)

/**
 * Press feedback for buttons (DESIGN.md §8): 0.96 scale on press,
 * spring back. The button's own ripple still fires — the scale is
 * on top of it, never instead of it. Hand [PressScale.interactionSource]
 * to the button and [PressScale.scale] to its graphicsLayer.
 */
internal class PressScale(
    val interactionSource: MutableInteractionSource,
    val scale: Float,
)

@Composable
internal fun rememberPressScale(): PressScale {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "pressScale",
    )
    return PressScale(interactionSource, scale)
}

/**
 * The gold-outline source pill (web `.pill`): the story's source in
 * 12px bold gold with a 1px gold border. The pill carries the source,
 * so the meta line below it carries the time only.
 */
@Composable
private fun SourcePill(
    source: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = StarBrookShapes.chip,
        color = Color.Transparent,
        border = BorderStroke(1.dp, StarBrookColors.gold),
    ) {
        Text(
            text = source,
            style = StarBrookType.label,
            color = StarBrookColors.gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**
 * The trust badge (web `.trust`), on EVERY card, always: "Confirmed
 * by N outlets" — gold when 3+ outlets confirm the story, muted
 * when it stands on one source. [outletCount] is the edition JSON's
 * `n` key; it defaults to 1 because [Story] does not carry `n` yet
 * (the data agent owns that field — until it lands, every card
 * honestly reads "Confirmed by 1 outlet", the web's own fallback
 * when `n` is absent).
 */
@Composable
fun TrustBadge(
    outletCount: Int = 1,
    modifier: Modifier = Modifier,
) {
    val confirmed = outletCount >= 3
    Surface(
        modifier = modifier,
        shape = StarBrookShapes.chip,
        color = if (confirmed) StarBrookColors.gold else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (confirmed) StarBrookColors.gold else StarBrookColors.border,
        ),
    ) {
        Text(
            text = if (outletCount == 1) "Confirmed by 1 outlet"
            else "Confirmed by $outletCount outlets",
            style = StarBrookType.label,
            color = if (confirmed) StarBrookColors.onGold else StarBrookColors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
}

/** A bookmark glyph, drawn — no icon-font dependency. Filled when saved. */
@Composable
private fun BookmarkGlyph(
    filled: Boolean,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.14f)
            lineTo(size.width * 0.72f, size.height * 0.14f)
            lineTo(size.width * 0.72f, size.height * 0.86f)
            lineTo(size.width * 0.50f, size.height * 0.68f)
            lineTo(size.width * 0.28f, size.height * 0.86f)
            close()
        }
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(
                path,
                color,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The card's bookmark toggle — a real button, 48dp tall, full width.
 * Gold-outline "Save to Shelf" when unsaved; filled gold "Saved to
 * Shelf" when saved. The state lives in the caller (the Room
 * saved-stories flow); this button only renders it and reports taps.
 * Press: ripple + 0.96 scale.
 */
@Composable
private fun SaveButton(
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = rememberPressScale()
    val buttonModifier = modifier
        .fillMaxWidth()
        .height(48.dp)
        .graphicsLayer(scaleX = press.scale, scaleY = press.scale)
    if (isSaved) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            interactionSource = press.interactionSource,
            shape = StarBrookShapes.chip,
            colors = ButtonDefaults.buttonColors(
                containerColor = StarBrookColors.gold,
                contentColor = StarBrookColors.onGold,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            BookmarkGlyph(filled = true, size = 18.dp, color = StarBrookColors.onGold)
            Text(
                text = "Saved to Shelf",
                style = StarBrookType.label,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            interactionSource = press.interactionSource,
            shape = StarBrookShapes.chip,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = StarBrookColors.gold,
            ),
            border = BorderStroke(1.dp, StarBrookColors.gold),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            BookmarkGlyph(filled = false, size = 18.dp, color = StarBrookColors.gold)
            Text(
                text = "Save to Shelf",
                style = StarBrookType.label,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The featured lead — the Mix's Top Stories lead, native. Big image,
 * Liberation Sans Bold headline, excerpt, source + time. Long
 * headlines wrap to three lines and ellipsize; nothing may push the
 * meta row off the card. [isSaved] / [onBookmarkClick] wire the
 * bookmark toggle to the caller's saved-stories flow; when
 * [onBookmarkClick] is null no bookmark renders. [outletCount] is
 * the edition JSON's `n` key (see [TrustBadge]; defaults to 1).
 */
@Composable
fun FeaturedCard(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onBookmarkClick: (() -> Unit)? = null,
    outletCount: Int = 1,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
        border = BorderStroke(1.dp, StarBrookColors.border),
    ) {
        Column {
            Box {
                StoryImage(
                    imageUrl = story.image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(StarBrookShapes.cardTop),
                )
                if (story.isOriginal) {
                    GoldOriginalBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val section = formatSection(story.section)
                if (section.isNotEmpty()) {
                    Text(
                        text = section.uppercase(),
                        style = StarBrookType.label,
                        color = StarBrookColors.gold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = story.title,
                    style = StarBrookType.headline,
                    color = StarBrookColors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!story.excerpt.isNullOrBlank()) {
                    Text(
                        text = story.excerpt,
                        style = StarBrookType.bodySmall,
                        color = StarBrookColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (story.isOriginal && !story.beat.isNullOrBlank()) {
                    Text(
                        text = "Reported by the StarBrook Newsroom — ${story.beat?.replaceFirstChar { it.uppercaseChar() }} Desk. Edited by CADE.",
                        style = StarBrookType.caption,
                        color = StarBrookColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${story.source} · ${formatRelativeTime(story.published)}",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TrustBadge(outletCount = outletCount)
                if (onBookmarkClick != null) {
                    SaveButton(isSaved = isSaved, onClick = onBookmarkClick)
                }
            }
        }
    }
}

/**
 * The standard story card, rebuilt to the web layout: vertical,
 * image on top (inside the card padding, 10dp radius, Live Star
 * fallback), gold-outline source pill, 19sp headline, time meta,
 * excerpt, trust badge, and the bookmark toggle. 1px #3A2E5C border,
 * 14dp radius (the site's `--r-lg`). A null excerpt simply omits
 * the excerpt line — the card never leaves a gap for it.
 *
 * [isSaved] / [onBookmarkClick] wire the bookmark toggle to the
 * caller's saved-stories flow (Room DAO / repository — no new API);
 * when [onBookmarkClick] is null no bookmark renders. [outletCount]
 * is the edition JSON's `n` key (see [TrustBadge]; defaults to 1).
 */
@Composable
fun NewsCard(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onBookmarkClick: (() -> Unit)? = null,
    outletCount: Int = 1,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = NewsCardShape,
        color = StarBrookColors.surface,
        border = BorderStroke(1.dp, StarBrookColors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StoryImage(
                imageUrl = story.image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(NewsCardImageShape),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourcePill(
                    source = story.source,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (story.isOriginal) {
                    GoldOriginalBadge()
                }
            }
            Text(
                text = story.title,
                style = StarBrookType.title,
                color = StarBrookColors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelativeTime(story.published),
                style = StarBrookType.caption,
                color = StarBrookColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!story.excerpt.isNullOrBlank()) {
                Text(
                    text = story.excerpt,
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (story.isOriginal && !story.beat.isNullOrBlank()) {
                Text(
                    text = "Reported by the StarBrook Newsroom — ${story.beat?.replaceFirstChar { it.uppercaseChar() }} Desk. Edited by CADE.",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TrustBadge(outletCount = outletCount)
            if (onBookmarkClick != null) {
                SaveButton(isSaved = isSaved, onClick = onBookmarkClick)
            }
        }
    }
}

/** A gold play triangle, drawn — no icon-font dependency. */
@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    val gold = StarBrookColors.gold
    val onGold = StarBrookColors.onGold
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(gold),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.16f)
                lineTo(size.width * 0.84f, size.height * 0.5f)
                lineTo(size.width * 0.28f, size.height * 0.84f)
                close()
            }
            drawPath(path, onGold)
        }
    }
}

/**
 * A daily briefing video. Poster 16:9 with a gold play badge and a
 * duration chip that never overlaps the title. Breaking flashes get
 * the live-red treatment on the chip. Tapping opens the native
 * Media3 player (video/{id}) — never a WebView, never a browser.
 */
@Composable
fun VideoCard(
    video: BriefingVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = StarBrookShapes.card,
        color = StarBrookColors.surface,
    ) {
        Column {
            Box {
                StoryImage(
                    imageUrl = video.posterUrl,
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(StarBrookShapes.cardTop),
                )
                // Dim the poster slightly so the play badge reads.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(StarBrookColors.background.copy(alpha = 0.2f)),
                )
                PlayBadge(modifier = Modifier.align(Alignment.Center))
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    shape = StarBrookShapes.chip,
                    color = if (video.isBreaking) StarBrookColors.liveRed
                    else StarBrookColors.background.copy(alpha = 0.8f),
                ) {
                    Text(
                        text = video.duration,
                        style = StarBrookType.label,
                        color = StarBrookColors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (video.isBreaking) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = StarBrookShapes.chip,
                        color = StarBrookColors.liveRed,
                    ) {
                        Text(
                            text = "BREAKING",
                            style = StarBrookType.label,
                            color = StarBrookColors.textPrimary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = video.title,
                    style = StarBrookType.title,
                    color = StarBrookColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.edition} briefing · ${video.date}",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
