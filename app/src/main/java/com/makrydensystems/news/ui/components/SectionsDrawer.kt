package com.makrydensystems.news.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookType

/**
 * Section navigation drawer — the app's answer to the website's nav drawer
 * (news_mix.html `.desknav` / `buildDrawerNav`): same labels, same order,
 * tap and go. On the website a drawer tap scrolls to that section on the
 * page and closes the drawer; here a tap selects the section on Home
 * (filter / scroll / route — see [DrawerSectionKind]).
 *
 * Labels + order are AUTHORITATIVE from the website drawer, and the
 * mapping below was verified live 2026-11-19 against both edition JSONs
 * (edition-morning.json + edition-evening.json, 15 stories each):
 * - Top Stories -> the whole edition, unfiltered (the website's topSection
 *   is the top of the page, not a story filter).
 * - Markets -> the edition's Markets section (its story carries
 *   section "business") + the atAGlance.markets strip ("S&P 500 7,324
 *   -0.2% · Dow 50,198 -1.1% · Nasdaq 24,430 +0.4%"). The strip itself is
 *   another agent's content work; this drawer owns navigation TO Markets.
 * - Weather -> the Weather screen (NWS, per-ZIP).
 * - Video -> the daily videos list from the briefing manifest (8 videos
 *   live 2026-11-19: 5 morning/evening briefings + 3 breaking flashes).
 * - Oklahoma -> section "local"; Culture -> "culture"; Sports -> "sports";
 *   Business -> "business"; Tech -> "tech"; Faith -> "faith";
 *   Science -> "science".
 * - The drawer deliberately does NOT list Entertainment / Gaming / Fashion
 *   (live edition sections, but not in the website drawer) or Saved.
 * - Markets and Business both resolve to story-section "business" because
 *   the live editions carry exactly ONE business story, and it sits in
 *   the Markets section. That is the real data, not a mapping bug.
 */
enum class DrawerSectionKind {
    /** Whole edition, no filter (Top Stories). */
    ALL,

    /** Filter Home to one edition section's stories. */
    STORIES,

    /** Markets view: the Markets section's stories + markets strip. */
    MARKETS,

    /** Show the full daily videos list on Home. */
    VIDEO,

    /** Navigate to the Weather screen. */
    WEATHER,
}

data class DrawerSection(
    val label: String,
    val kind: DrawerSectionKind,
    /** Lowercase `section` value in edition-JSON stories, for STORIES/MARKETS. */
    val storySection: String? = null,
)

val DRAWER_SECTIONS: List<DrawerSection> = listOf(
    DrawerSection("Top Stories", DrawerSectionKind.ALL),
    DrawerSection("Markets", DrawerSectionKind.MARKETS, "business"),
    DrawerSection("Weather", DrawerSectionKind.WEATHER),
    DrawerSection("Video", DrawerSectionKind.VIDEO),
    DrawerSection("Oklahoma", DrawerSectionKind.STORIES, "local"),
    DrawerSection("Culture", DrawerSectionKind.STORIES, "culture"),
    DrawerSection("Sports", DrawerSectionKind.STORIES, "sports"),
    DrawerSection("Business", DrawerSectionKind.STORIES, "business"),
    DrawerSection("Tech", DrawerSectionKind.STORIES, "tech"),
    DrawerSection("Faith", DrawerSectionKind.STORIES, "faith"),
    DrawerSection("Science", DrawerSectionKind.STORIES, "science"),
)

/**
 * Modal navigation drawer hosting the section list. Rows are real
 * [NavigationDrawerItem] buttons (56dp rows: above the 48dp touch
 * target, with built-in pressed + selected states) — never plain text.
 *
 * The caller hoists the [DrawerState] so content inside [content] can
 * open the drawer (e.g. a Sections button on Home).
 */
@Composable
fun SectionsDrawer(
    drawerState: DrawerState,
    selected: DrawerSection,
    onSelect: (DrawerSection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = StarBrookColors.surface,
                drawerContentColor = StarBrookColors.textPrimary,
            ) {
                Text(
                    text = "STARBROOK NEWS",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, end = 24.dp),
                )
                Text(
                    text = "Sections",
                    style = StarBrookType.title,
                    color = StarBrookColors.textPrimary,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp, end = 24.dp),
                )
                Text(
                    text = "Tap a section to jump to it",
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                    modifier = Modifier.padding(start = 28.dp, end = 24.dp, bottom = 12.dp),
                )
                HorizontalDivider(color = StarBrookColors.border)
                for (section in DRAWER_SECTIONS) {
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = section.label,
                                style = StarBrookType.body,
                            )
                        },
                        selected = section == selected,
                        onClick = { onSelect(section) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = StarBrookColors.goldsoft,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = StarBrookColors.textPrimary,
                            unselectedTextColor = StarBrookColors.textPrimary,
                        ),
                    )
                }
            }
        },
        content = content,
    )
}
