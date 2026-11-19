package com.makrydensystems.news.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * The featured lead — the Mix's Top Stories lead, native. Big image,
 * Liberation Sans Bold headline, excerpt, source + time. Long
 * headlines wrap to three lines and ellipsize; nothing may push the
 * meta row off the card.
 */
@Composable
fun FeaturedCard(
    story: Story,
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
                        text = "Reported by the StarBrook Newsroom — ${story.beat} Desk. Edited by CADE.",
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
            }
        }
    }
}

/**
 * The standard story row. Text takes the space; the thumbnail is a
 * fixed 96dp square that never squeezes the headline. A null excerpt
 * simply omits the excerpt line — the card never leaves a gap for it.
 */
@Composable
fun NewsCard(
    story: Story,
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
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (story.isOriginal) {
                    GoldOriginalBadge()
                }
                Text(
                    text = story.title,
                    style = StarBrookType.title,
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
                Text(
                    text = "${story.source} · ${formatRelativeTime(story.published)}",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StoryImage(
                imageUrl = story.image,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(StarBrookShapes.card),
            )
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
