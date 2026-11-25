package com.makrydensystems.news.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * - Business -> the edition's Business section (its story carries
 *   section "business") + the atAGlance.markets strip ("S&P 500 7,324
 *   -0.2% · Dow 50,198 -1.1% · Nasdaq 24,430 +0.4%"). The strip itself is
 *   another agent's content work; this drawer owns navigation TO Business.
 * - Weather -> the Weather screen (NWS, per-ZIP).
 * - Video -> the daily videos list from the briefing manifest (8 videos
 *   live 2026-11-19: 5 morning/evening briefings + 3 breaking flashes).
 * - Oklahoma -> section "local"; Culture -> "culture"; Sports -> "sports";
 *   Tech -> "tech"; Faith -> "faith"; Science -> "science".
 * - Entertainment -> "entertainment"; Gaming -> "gaming";
 *   Fashion -> "fashion" (R-02, 2026-11-24: the website drawer does not
 *   list these three, but the app must — without a row they were reachable
 *   only by guessing a search query).
 * - Saved is deliberately not listed (it lives in the bottom bar).
 * - Business was "Markets" until Build #26: the drawer carried both a
 *   MARKETS row and a STORIES "Business" row resolving to the same
 *   "business" stories, so the rename merged them into the one row below.
 */
enum class DrawerSectionKind {
    /** Whole edition, no filter (Top Stories). */
    ALL,

    /** Filter Home to one edition section's stories. */
    STORIES,

    /** Business view: the Business section's stories + markets strip. */
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
    DrawerSection("Business", DrawerSectionKind.MARKETS, "business"),
    DrawerSection("Weather", DrawerSectionKind.WEATHER),
    DrawerSection("Video", DrawerSectionKind.VIDEO),
    DrawerSection("National", DrawerSectionKind.STORIES, "national"),
    DrawerSection("Oklahoma", DrawerSectionKind.STORIES, "local"),
    DrawerSection("Culture", DrawerSectionKind.STORIES, "culture"),
    DrawerSection("Sports", DrawerSectionKind.STORIES, "sports"),
    DrawerSection("Tech", DrawerSectionKind.STORIES, "tech"),
    DrawerSection("Faith", DrawerSectionKind.STORIES, "faith"),
    DrawerSection("Science", DrawerSectionKind.STORIES, "science"),
    DrawerSection("Entertainment", DrawerSectionKind.STORIES, "entertainment"),
    DrawerSection("Gaming", DrawerSectionKind.STORIES, "gaming"),
    DrawerSection("Fashion", DrawerSectionKind.STORIES, "fashion"),
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
                // R-02 (2026-11-24): ModalDrawerSheet does not scroll its
                // content on its own — with fourteen rows the bottom
                // entries clip on short screens / large font scales, which
                // would leave the new Entertainment / Gaming / Fashion
                // rows unreachable. The scrollable Column is the
                // documented Material 3 pattern; no visual change when
                // everything fits.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
            }
        },
        content = content,
    )
}
