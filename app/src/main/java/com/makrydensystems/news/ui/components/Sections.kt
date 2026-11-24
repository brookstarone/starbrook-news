package com.makrydensystems.news.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makrydensystems.news.data.MarketsData
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * Business (Wave 2): the edition's at-a-glance ticker line plus the
 * edition's Business section stories. Both come from the edition JSON
 * ([MarketsData]); the header is the section's own JSON name
 * ([MarketsData.sectionName]) — never hardcoded. Hidden entirely when
 * the edition carries neither (the repository emits null then), never
 * an empty card.
 */
@Composable
fun MarketsSection(
    markets: MarketsData,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = markets.sectionName)
        if (markets.markets.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = StarBrookShapes.card,
                color = StarBrookColors.surface,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = markets.markets,
                        style = StarBrookType.body,
                        color = StarBrookColors.textPrimary,
                    )
                    if (!markets.marketsLabel.isNullOrBlank()) {
                        Text(
                            text = markets.marketsLabel,
                            style = StarBrookType.caption,
                            color = StarBrookColors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        markets.stories.forEach { story ->
            NewsCard(story = story, onClick = { onStoryClick(story) }, outletCount = story.outletCount)
        }
    }
}

/**
 * StarBrook Originals (Wave 2): the newsroom's own reporting, fetched
 * from PostgREST (news_original_stories, published rows). Every card
 * carries the gold "StarBrook Original" badge via [NewsCard] (the
 * [Story.isOriginal] flag the Originals pipeline fills). Tapping one
 * opens the read view at its ?original= link, which resolves the
 * stored full body — no extraction call.
 */
@Composable
fun OriginalsSection(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = "StarBrook Originals")
        stories.forEach { story ->
            NewsCard(story = story, onClick = { onStoryClick(story) }, outletCount = story.outletCount)
        }
    }
}
