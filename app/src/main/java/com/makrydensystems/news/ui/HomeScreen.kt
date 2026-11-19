package com.makrydensystems.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.NewsRepository
import com.makrydensystems.news.data.SettingsStore
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.BriefingVideo
import com.makrydensystems.news.data.model.Breaking
import com.makrydensystems.news.data.model.LiveItem
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.components.BreakingStrip
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.ErrorState
import com.makrydensystems.news.ui.components.FeaturedCard
import com.makrydensystems.news.ui.components.LiveNowRow
import com.makrydensystems.news.ui.components.NewsCard
import com.makrydensystems.news.ui.components.SectionHeader
import com.makrydensystems.news.ui.components.SkeletonList
import com.makrydensystems.news.ui.components.VideoCard
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class HomeViewModel(
    private val repo: NewsRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    val edition: StateFlow<String> = settings.editionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "morning")

    val stories: StateFlow<FeedState<List<Story>>> = edition
        .flatMapLatest { repo.editionsFlow(it) }
        .combine(settings.sectionsFlow) { list, enabled ->
            list.filter { story -> story.section.lowercase() in enabled }
        }
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    val breaking = repo.breakingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val liveNow = repo.liveNowFlow()
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    val videos = repo.videosFlow()
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    fun setEdition(edition: String) {
        viewModelScope.launch { settings.setEdition(edition) }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repo.refreshAll() }
        }
    }
}

@Composable
fun HomeScreen(
    container: AppContainer,
    onStoryClick: (Story) -> Unit,
    onVideoClick: (BriefingVideo) -> Unit,
    onBreakingClick: (Breaking) -> Unit,
    onLiveClick: (LiveItem) -> Unit,
    onSearchClick: () -> Unit,
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
    var showAllLive by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        item {
            BreakingStrip(
                breaking = breaking,
                onClick = { breaking?.let { onBreakingClick(it) } },
            )
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
                val stories = state.value
                if (stories.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No stories in this edition yet",
                            body = "The next edition lands soon — pull down to check again.",
                            actionLabel = "Try again",
                            onAction = { vm.refresh() },
                        )
                    }
                } else {
                    item {
                        SectionHeader(title = if (edition == "evening") "Evening Edition" else "Morning Edition")
                    }
                    item {
                        FeaturedCard(story = stories.first(), onClick = { onStoryClick(stories.first()) })
                    }
                    items(stories.drop(1)) { story ->
                        NewsCard(story = story, onClick = { onStoryClick(story) })
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
                            Text(
                                text = if (showAllLive) "Show less" else "See all live updates",
                                style = StarBrookType.label,
                                color = StarBrookColors.gold,
                                modifier = Modifier.clickable { showAllLive = !showAllLive },
                            )
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
                items(state.value.take(3)) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) })
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
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
                modifier = Modifier.clickable { onSelect(key) },
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
