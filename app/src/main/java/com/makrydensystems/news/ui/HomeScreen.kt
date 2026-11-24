package com.makrydensystems.news.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.MarketsData
import com.makrydensystems.news.data.NewsRepository
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.BriefingVideo
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.components.BreakingStrip
import com.makrydensystems.news.ui.components.DRAWER_SECTIONS
import com.makrydensystems.news.ui.components.DrawerSection
import com.makrydensystems.news.ui.components.DrawerSectionKind
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.ErrorState
import com.makrydensystems.news.ui.components.FeaturedCard
import com.makrydensystems.news.ui.components.LiveNowRow
import com.makrydensystems.news.ui.components.LiveTicker
import com.makrydensystems.news.ui.components.MarketsSection
import com.makrydensystems.news.ui.components.NewsCard
import com.makrydensystems.news.ui.components.OriginalsSection
import com.makrydensystems.news.ui.components.SectionHeader
import com.makrydensystems.news.ui.components.SectionsDrawer
import com.makrydensystems.news.ui.components.SkeletonList
import com.makrydensystems.news.ui.components.VideoCard
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class HomeViewModel(
    private val repo: NewsRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    val edition: StateFlow<String> = settings.editionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "morning")

    // Drawer section selection (SectionsDrawer): null = Top Stories = the
    // whole edition, unfiltered. Any other value filters Home to that
    // section's stories (STORIES/MARKETS), expands the videos list
    // (VIDEO), or routes off Home (WEATHER — via HomeScreen's callback).
    private val _selectedSection = MutableStateFlow<DrawerSection?>(null)
    val selectedSection: StateFlow<DrawerSection?> = _selectedSection

    fun selectSection(section: DrawerSection?) {
        _selectedSection.value = section
    }

    // Retry trigger (DEFECT 1, design-audit gap #5): the feed flows below
    // are one-shot `flow {}` builders — each runs once (cache, then one
    // refresh) and completes, and the DAO reads are suspend one-shots, not
    // observable Flows. So repo.refreshAll() writing fresh rows to Room
    // never re-emitted: the Try again button re-fetched into the void and
    // the ErrorState stayed forever. Bumping this tick re-collects the
    // flows, which re-runs the fetch and emits the new result. Every
    // Try again on this screen taps refresh() below.
    private val refreshTick = MutableStateFlow(0)

    // Pull-to-refresh state (Home UI): true from the pull until the
    // feed re-emits (cache first, so this clears as soon as the
    // re-rendered feed is on screen; the quiet background refresh
    // keeps going). Guarded so a second pull / Try again while a
    // refresh is already running is a no-op.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val stories: StateFlow<FeedState<List<Story>>> = combine(
        refreshTick.flatMapLatest { edition.flatMapLatest { repo.editionsFlow(it) } },
        settings.sectionsFlow,
        selectedSection,
    ) { list, enabled, selected ->
            // R-02 (2026-11-24): SettingsStore.ALL_SECTIONS now holds all
            // eleven live sections, so the prefs filter alone no longer
            // drops Sports / Faith / Science / Entertainment / Gaming /
            // Fashion (6 of 17 in the 2026-11-24 morning edition). A
            // drawer section's stories still always pass — the active
            // drawer section overrides a Settings toggle-off, and Home
            // then filters the displayed list to that section. With no
            // drawer section active this is the prefs filter on its own.
            val active = if (selected != null &&
                (selected.kind == DrawerSectionKind.STORIES ||
                    selected.kind == DrawerSectionKind.MARKETS)
            ) selected.storySection else null
            list.filter { story ->
                val section = story.section.lowercase()
                section in enabled || (active != null && section == active)
            }
        }
        .asFeedState()
        .onEach { if (it is FeedState.Data || it is FeedState.Error) _isRefreshing.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    val breaking = refreshTick
        .flatMapLatest { repo.breakingFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val liveNow = refreshTick
        .flatMapLatest { repo.liveNowFlow() }
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    val videos = refreshTick
        .flatMapLatest { repo.videosFlow() }
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    // Breaking flash, top of Home (2026-11-23, Nvidia gap): the manifest
    // lists newest first, so the first entry is the newest content. When
    // that entry is a breaking flash it gets a prominent card at the top
    // of Home — independent of breaking.json, which clears within hours
    // and left the flash buried at the bottom of the list. Once a newer
    // briefing lands, the flash drops back to the list below.
    val topBreakingVideo: StateFlow<BriefingVideo?> = videos
        .map { state ->
            if (state is FeedState.Data) {
                state.value.firstOrNull()?.takeIf { it.isBreaking }
            } else {
                null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // "New breaking video" update prompt: the newest breaking video the
    // user has not seen yet (seen = tapped to play, persisted in
    // DataStore). Null when there is nothing new — the banner never
    // re-prompts for a video already watched.
    val newBreakingVideo: StateFlow<BriefingVideo?> = combine(
        videos,
        settings.lastSeenBreakingVideoIdFlow,
    ) { state, seenId ->
        if (state is FeedState.Data) {
            state.value.firstOrNull()?.takeIf { it.isBreaking && it.id != seenId }
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Tapping any breaking video marks it seen (the banner goes away).
    fun markBreakingVideoSeen(id: String) {
        viewModelScope.launch { settings.setLastSeenBreakingVideoId(id) }
    }

    // Wave 2: Originals (PostgREST, published rows) and Business (the
    // current edition's at-a-glance line + Business section). Both ride
    // the same refreshTick retry as the other feeds. Originals is empty
    // until the anon key is wired (TODO.md §1) — the section then hides.
    val originals: StateFlow<FeedState<List<Story>>> = refreshTick
        .flatMapLatest { repo.originalsFlow() }
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    val markets: StateFlow<MarketsData?> = refreshTick
        .flatMapLatest { edition.flatMapLatest { repo.marketsFlow(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEdition(edition: String) {
        viewModelScope.launch { settings.setEdition(edition) }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        refreshTick.value += 1
    }

    // Bookmark state (Home UI): the EXISTING saved-stories source —
    // Room DAO via the repository. No new API; the portal agent keeps
    // this source in sync with the portal Shelf.
    fun isSavedFlow(link: String) = repo.isSavedFlow(link)

    fun toggleSave(story: Story, isSaved: Boolean) {
        viewModelScope.launch {
            if (isSaved) repo.unsaveStory(story.link) else repo.saveStory(story)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onStoryClick: (Story) -> Unit,
    onVideoClick: (BriefingVideo) -> Unit,
    onBreakingClick: (Breaking) -> Unit,
    onLiveClick: (LiveItem) -> Unit,
    onSearchClick: () -> Unit,
    // Drawer "Weather" destination. Default no-op so existing callers
    // compile; MainActivity wires this to the Weather route.
    onWeatherClick: () -> Unit = {},
    // Bookmark seam (portal agent owns the receiving side): [isSignedIn]
    // comes from the portal session (MainActivity collects it) and
    // [onPortalClick] opens "portal?reason=save" (MainActivity wires it).
    isSignedIn: Boolean = false,
    onPortalClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(container.newsRepository, container.settingsStore)
            }
        },
    )
    val edition by vm.edition.collectAsState()
    val storiesState by vm.stories.collectAsState()
    val breaking by vm.breaking.collectAsState()
    val liveState by vm.liveNow.collectAsState()
    val videosState by vm.videos.collectAsState()
    val topBreakingVideo by vm.topBreakingVideo.collectAsState()
    val newBreakingVideo by vm.newBreakingVideo.collectAsState()
    val originalsState by vm.originals.collectAsState()
    val marketsData by vm.markets.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    var showAllLive by remember { mutableStateOf(false) }

    // Bookmark: the story ALWAYS toggles the Shelf (Room, via the repo)
    // — guest saves are local-only, per the portal data-layer policy.
    // A signed-out SAVE additionally offers sign-in right there
    // (onPortalClick -> "portal?reason=save"); unsaving never prompts.
    val onBookmark: (Story, Boolean) -> Unit = { story, saved ->
        vm.toggleSave(story, saved)
        if (!isSignedIn && !saved) onPortalClick()
    }

    // Tapping any breaking video marks it seen (the update banner goes
    // away) — the top card, the banner, and the bottom list all route
    // through here, so every path to the video counts as seeing it.
    val handleVideoClick: (BriefingVideo) -> Unit = { video ->
        if (video.isBreaking) vm.markBreakingVideoSeen(video.id)
        onVideoClick(video)
    }

    // Section navigation (drawer): null = Top Stories = whole edition.
    val selectedSection by vm.selectedSection.collectAsState()
    val drawerSelected = selectedSection ?: DRAWER_SECTIONS.first()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Index of the "Video briefings" header in the LazyColumn, computed
    // from the same states that build the list: sections button, search,
    // update banner (when an unseen breaking video exists), breaking,
    // top breaking card (when the newest video is a flash), ticker,
    // edition switcher, stories block, spacer, Live header, live block,
    // spacer. Coerced at scroll time against the real count.
    val videoHeaderIndex: () -> Int = {
        val storyItems = when (val state = storiesState) {
            is FeedState.Loading -> 1
            is FeedState.Error -> 1
            is FeedState.Data -> {
                val visible = visibleStoriesFor(state.value, selectedSection)
                if (visible.isEmpty()) 1 else visible.size + 1
            }
        }
        val liveItems = when (val state = liveState) {
            is FeedState.Loading -> 1
            is FeedState.Error -> 1
            is FeedState.Data -> {
                val shown = if (showAllLive) state.value.size else state.value.take(3).size
                shown + if (state.value.size > 3) 1 else 0
            }
        }
        // The LIVE ticker is one item when the live feed has data
        // (it sits right after the breaking strip, web order).
        val tickerItems = when (val state = liveState) {
            is FeedState.Data -> if (state.value.isNotEmpty()) 1 else 0
            else -> 0
        }
        // The update banner and the top breaking card sit above the
        // stories, so each shifts the target down by one when present.
        val bannerItems = (if (newBreakingVideo != null) 1 else 0) +
            (if (topBreakingVideo != null) 1 else 0)
        7 + bannerItems + tickerItems + storyItems + liveItems
    }

    // One drawer tap = close the drawer, then go: Weather routes off
    // Home, Video scrolls to the (expanded) videos list, every story
    // section scrolls to the top of its filtered list. Mirrors the
    // website drawer (navGo + closeDrawer in news_new_main.js).
    fun onDrawerSelect(section: DrawerSection) {
        vm.selectSection(if (section.kind == DrawerSectionKind.ALL) null else section)
        scope.launch {
            drawerState.close()
            when (section.kind) {
                DrawerSectionKind.WEATHER -> onWeatherClick()
                DrawerSectionKind.VIDEO -> {
                    val target = videoHeaderIndex()
                        .coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    listState.animateScrollToItem(target)
                }
                else -> listState.animateScrollToItem(0)
            }
        }
    }

    SectionsDrawer(
        drawerState = drawerState,
        selected = drawerSelected,
        onSelect = ::onDrawerSelect,
        modifier = modifier,
    ) {
    // Pull-to-refresh (the empty state promises it): the same
    // refresh() the Try again buttons use — one tick re-collects
    // every feed. The indicator clears when the feed re-emits.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Sections drawer entry — the website's nav drawer, native.
            // The gold label shows the active section.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { scope.launch { drawerState.open() } },
                shape = StarBrookShapes.chip,
                color = StarBrookColors.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Sections",
                        style = StarBrookType.body,
                        color = StarBrookColors.textPrimary,
                    )
                    Text(
                        text = drawerSelected.label,
                        style = StarBrookType.label,
                        color = StarBrookColors.gold,
                    )
                }
            }
        }
        item {
            // Search entry — a real field, not decoration.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable(onClick = onSearchClick),
                shape = StarBrookShapes.chip,
                color = StarBrookColors.surfaceVariant,
            ) {
                Text(
                    text = "Search StarBrook News…",
                    style = StarBrookType.bodySmall,
                    color = StarBrookColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        // UPDATE BANNER (2026-11-23, Nvidia gap): a breaking flash that
        // arrived since the user last looked gets an explicit Update
        // prompt at the very top — no silent burial below the fold.
        // Tapping it plays the flash and marks it seen.
        newBreakingVideo?.let { video ->
            item {
                NewBreakingVideoBanner(
                    video = video,
                    onWatch = { handleVideoClick(video) },
                )
            }
        }
        item {
            BreakingStrip(
                breaking = breaking,
                onClick = { breaking?.let { onBreakingClick(it) } },
            )
        }
        // TOP BREAKING VIDEO CARD (same gap): when the manifest's newest
        // entry is a breaking flash it sits here as a full card —
        // independent of breaking.json, which clears within hours.
        topBreakingVideo?.let { video ->
            item {
                VideoCard(video = video, onClick = { handleVideoClick(video) })
            }
        }
        // LIVE ticker (web .liveticker, right under the breaking
        // strip): the live items, one headline at a time, tappable
        // to the story. Same latest.json feed as Live now below —
        // hidden entirely when there is nothing live.
        when (val state = liveState) {
            is FeedState.Data -> {
                if (state.value.isNotEmpty()) {
                    item {
                        LiveTicker(
                            items = state.value,
                            onItemClick = { onLiveClick(it) },
                        )
                    }
                }
            }
            else -> { /* Loading / Error: no ticker; Live now shows its own state. */ }
        }
        item {
            EditionSwitcher(
                edition = edition,
                onSelect = { vm.setEdition(it) },
            )
        }
        when (val state = storiesState) {
            is FeedState.Loading -> item { SkeletonList(count = 5) }
            is FeedState.Error -> item {
                ErrorState(message = state.message, onRetry = { vm.refresh() })
            }
            is FeedState.Data -> {
                val active = selectedSection
                val stories = visibleStoriesFor(state.value, active)
                if (stories.isEmpty()) {
                    item {
                        if (active == null) {
                            EmptyState(
                                title = "No stories in this edition yet",
                                body = "The next edition lands soon — pull down to check again.",
                                actionLabel = "Try again",
                                onAction = { vm.refresh() },
                            )
                        } else {
                            EmptyState(
                                title = "No ${active.label} stories in this edition",
                                body = "Try the other edition, or go back to Top Stories to see everything.",
                                actionLabel = "Show all stories",
                                onAction = { vm.selectSection(null) },
                            )
                        }
                    }
                } else {
                    item {
                        SectionHeader(
                            title = active?.label
                                ?: if (edition == "evening") "Evening Edition" else "Morning Edition",
                        )
                    }
                    item {
                        val first = stories.first()
                        val firstSavedFlow = remember(first.link) { vm.isSavedFlow(first.link) }
                        val firstSaved by firstSavedFlow.collectAsState(initial = false)
                        FeaturedCard(
                            story = first,
                            onClick = { onStoryClick(first) },
                            isSaved = firstSaved,
                            onBookmarkClick = { onBookmark(first, firstSaved) },
                            outletCount = first.outletCount,
                        )
                    }
                    items(stories.drop(1)) { story ->
                        val savedFlow = remember(story.link) { vm.isSavedFlow(story.link) }
                        val saved by savedFlow.collectAsState(initial = false)
                        NewsCard(
                            story = story,
                            onClick = { onStoryClick(story) },
                            isSaved = saved,
                            onBookmarkClick = { onBookmark(story, saved) },
                            outletCount = story.outletCount,
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item { SectionHeader(title = "Live now") }
        when (val state = liveState) {
            is FeedState.Loading -> item { SkeletonList(count = 3) }
            is FeedState.Error -> item {
                ErrorState(message = state.message, onRetry = { vm.refresh() })
            }
            is FeedState.Data -> {
                val items = if (showAllLive) state.value else state.value.take(3)
                items(items) { live ->
                    LiveNowRow(item = live, onClick = { onLiveClick(live) })
                }
                if (state.value.size > 3) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = { showAllLive = !showAllLive },
                                shape = StarBrookShapes.chip,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = StarBrookColors.gold,
                                ),
                                border = BorderStroke(1.dp, StarBrookColors.gold),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text(
                                    text = if (showAllLive) "Show less" else "See all live updates",
                                    style = StarBrookType.label,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item { SectionHeader(title = "Video briefings") }
        when (val state = videosState) {
            is FeedState.Loading -> item { SkeletonList(count = 2) }
            is FeedState.Error -> item {
                ErrorState(message = state.message, onRetry = { vm.refresh() })
            }
            is FeedState.Data -> {
                // Drawer "Video" shows the whole daily list (briefings +
                // flashes — 8 live 2026-11-19); otherwise the home row
                // shows the latest 3.
                val videos = if (selectedSection?.kind == DrawerSectionKind.VIDEO) {
                    state.value
                } else {
                    state.value.take(3)
                }
                items(videos) { video ->
                    VideoCard(video = video, onClick = { handleVideoClick(video) })
                }
            }
        }
        // SECTIONS (Wave 2): Originals + Business. They render only on
        // Top Stories (no drawer filter active — a filter means "only
        // this section") and sit BELOW the video block on purpose:
        // videoHeaderIndex above counts the items above "Video
        // briefings", and anything inserted above it would shift the
        // drawer scroll target. Originals come from PostgREST (empty
        // until the anon key is wired — TODO.md §1); Business from the
        // current edition's atAGlance + Business section.
        if (selectedSection == null) {
            when (val state = originalsState) {
                is FeedState.Loading -> {}
                is FeedState.Error -> item {
                    ErrorState(message = state.message, onRetry = { vm.refresh() })
                }
                is FeedState.Data -> {
                    if (state.value.isNotEmpty()) {
                        item {
                            OriginalsSection(stories = state.value, onStoryClick = onStoryClick)
                        }
                    }
                }
            }
            marketsData?.let { markets ->
                item {
                    MarketsSection(markets = markets, onStoryClick = onStoryClick)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
    }
    }
}

/**
 * Stories shown for the active drawer section (null = Top Stories =
 * the whole edition, unfiltered). STORIES and MARKETS both filter by
 * the edition JSON's lowercase `section` value — the Business drawer
 * entry filters to "business".
 */
private fun visibleStoriesFor(stories: List<Story>, active: DrawerSection?): List<Story> {
    val wanted: String? = if (active != null &&
        (active.kind == DrawerSectionKind.STORIES || active.kind == DrawerSectionKind.MARKETS)
    ) {
        active.storySection
    } else {
        null
    } ?: return stories
    return stories.filter { it.section.lowercase() == wanted }
}

@Composable
private fun EditionSwitcher(
    edition: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((key, label) in listOf("morning" to "Morning", "evening" to "Evening")) {
            val selected = edition == key
            Surface(
                // 48dp touch target (buttons mandate): the pills were
                // ~36dp before.
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(key) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) StarBrookColors.gold else StarBrookColors.surfaceVariant,
            ) {
                Text(
                    text = label,
                    style = StarBrookType.label,
                    color = if (selected) StarBrookColors.onGold else StarBrookColors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * "New breaking video" update prompt (2026-11-23, Nvidia gap): the
 * visible Update affordance for breaking content. Gold, one row, the
 * whole row taps through to play the flash. Shows only while the
 * newest breaking video is unseen — once played, the id is persisted
 * and the banner never re-prompts for it.
 */
@Composable
private fun NewBreakingVideoBanner(
    video: BriefingVideo,
    onWatch: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onWatch),
        shape = StarBrookShapes.card,
        color = StarBrookColors.gold,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "UPDATE — NEW BREAKING VIDEO",
                    style = StarBrookType.label,
                    color = StarBrookColors.onGold,
                    maxLines = 1,
                )
                Text(
                    text = video.title,
                    style = StarBrookType.body,
                    color = StarBrookColors.onGold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Watch",
                style = StarBrookType.label,
                color = StarBrookColors.onGold,
                maxLines = 1,
            )
        }
    }
}
