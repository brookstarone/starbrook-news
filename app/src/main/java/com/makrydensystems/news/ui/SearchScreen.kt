package com.makrydensystems.news.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.NewsRepository
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.NewsCard
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
private class SearchViewModel(private val repo: NewsRepository) : ViewModel() {
    val query = MutableStateFlow("")

    /**
     * Last completed search, kept on screen while the next query loads —
     * the instant standard: a new keystroke never blanks the list into
     * skeletons. The loading state is a thin gold line under the field,
     * never a skeleton flash.
     */
    val results = MutableStateFlow<List<Story>>(emptyList())
    private val completedQuery = MutableStateFlow("")

    /**
     * True from the first keystroke of a new query until the live
     * editions have had their say for it. Stays true while the live
     * load is still in flight, so the screen never flashes a "no
     * match" / "couldn't load" state for a search that is still running.
     */
    private val liveLoadDone = MutableStateFlow(false)
    val searching: StateFlow<Boolean> =
        combine(query, completedQuery, liveLoadDone) { q, done, loaded ->
            q.isNotBlank() && (q.trim() != done || !loaded)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val refreshTick = MutableStateFlow(0)
    val refreshing = MutableStateFlow(false)

    /**
     * Live current editions (morning + evening), fetched once per collect
     * and again on pull-to-refresh. Search merges these with the Room
     * cache so a story in today's live edition matches even before it
     * lands in the cache — search is real search, not cache-only.
     */
    private val editionStories: StateFlow<List<Story>> = refreshTick
        .flatMapLatest {
            combine(repo.editionsFlow("morning"), repo.editionsFlow("evening")) { morning, evening ->
                (morning + evening).distinctBy { it.link }
            }.catch { emit(emptyList()) }
        }
        .onEach { liveLoadDone.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the live editions hold any stories at all — the offline
     * empty state shows only when the live load finished AND came back
     * empty, never while it is still loading. Declared after
     * [editionStories] on purpose: property initializers run in order. */
    val liveLoaded: StateFlow<Boolean> = editionStories.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            query.debounce(300).collectLatest { q ->
                val trimmed = q.trim()
                if (trimmed.isBlank()) {
                    results.value = emptyList()
                    completedQuery.value = ""
                } else {
                    combine(repo.searchStories(trimmed), editionStories) { cached, live ->
                        rankSearch(trimmed, cached, live)
                    }.collect { ranked ->
                        results.value = ranked
                        completedQuery.value = trimmed
                    }
                }
            }
        }
    }

    /**
     * Pull-to-refresh: re-fetch both live editions through the
     * repository (Room + live list update, which re-runs the current
     * search). Best-effort — offline just leaves the cached results
     * in place; the extra tick re-collect is the quiet background
     * refresh the rest of the app uses.
     */
    fun refresh() {
        viewModelScope.launch {
            if (refreshing.value) return@launch
            refreshing.value = true
            try {
                runCatching { repo.refreshAll() }
                refreshTick.value += 1
            } finally {
                refreshing.value = false
            }
        }
    }
}

/**
 * Ranked search over the merged cache + live editions. Title matches
 * first, then title word matches, then source, then excerpt, then
 * any-word matches across title/source/excerpt; newest first inside
 * each rank. A story that matches nothing is dropped (the live
 * editions arrive unfiltered, unlike the Room LIKE query). A story
 * carried in both editions is ranked under EACH title and deduped
 * after sorting, so it keeps its best-matching title — the pipeline
 * rewrites titles between editions, and the morning title may be the
 * one the query matches.
 */
private fun rankSearch(query: String, cached: List<Story>, live: List<Story>): List<Story> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return emptyList()
    val words = q.split(Regex("\\s+")).filter { it.length > 1 }
    return (cached + live).mapNotNull { story ->
        val title = story.title.lowercase()
        val source = story.source.lowercase()
        val excerpt = story.excerpt?.lowercase().orEmpty()
        val rank = when {
            title.contains(q) -> 0
            words.isNotEmpty() && words.all { title.contains(it) } -> 1
            source.contains(q) -> 2
            excerpt.contains(q) -> 3
            words.isNotEmpty() && words.all { "$title $source $excerpt".contains(it) } -> 4
            else -> return@mapNotNull null
        }
        rank to story
    }
        .sortedWith(compareBy({ it.first }, { -it.second.published }))
        .distinctBy { it.second.link }
        .map { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    onStoryClick: (Story) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(container.newsRepository) } },
    )
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val liveLoaded by vm.liveLoaded.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                containerColor = StarBrookColors.surface,
                color = StarBrookColors.gold,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(
                    onClick = onBack,
                    shape = StarBrookShapes.chip,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StarBrookColors.gold,
                    ),
                    border = BorderStroke(1.dp, StarBrookColors.gold),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(text = "‹ Back", style = StarBrookType.label)
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text("Search stories…", color = StarBrookColors.textSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = StarBrookShapes.chip,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StarBrookColors.gold,
                            unfocusedBorderColor = StarBrookColors.border,
                            focusedTextColor = StarBrookColors.textPrimary,
                            unfocusedTextColor = StarBrookColors.textPrimary,
                            cursorColor = StarBrookColors.gold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (query.isNotBlank()) {
                        TextButton(
                            onClick = { vm.query.value = "" },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = StarBrookColors.gold,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(text = "Clear", style = StarBrookType.label)
                        }
                    }
                }
            }
            // Loading state: a thin gold line under the field, with the
            // previous results still on screen. Never a skeleton flash.
            if (searching) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            color = StarBrookColors.gold,
                            trackColor = StarBrookColors.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Searching…",
                            style = StarBrookType.caption,
                            color = StarBrookColors.textSecondary,
                        )
                    }
                }
            }
            if (query.isBlank()) {
                item {
                    EmptyState(
                        title = "Search StarBrook",
                        body = "Try a name, a place, or a topic — results come from today's editions, live.",
                    )
                }
            } else if (results.isNotEmpty()) {
                items(results) { story ->
                    NewsCard(story = story, onClick = { onStoryClick(story) }, outletCount = story.outletCount)
                }
            } else if (!searching && !liveLoaded) {
                // Live load finished and came back empty: offline or the
                // editions haven't landed — not "no match". Pull down
                // (or the button) retries the live fetch.
                item {
                    EmptyState(
                        title = "Couldn't load the latest edition",
                        body = "Check your connection, then pull down to try again.",
                        actionLabel = "Try again",
                        onAction = { vm.refresh() },
                    )
                }
            } else if (!searching) {
                item {
                    EmptyState(
                        title = "No stories matched \"$query\"",
                        body = "Try different words, or pull down to check the latest edition.",
                    )
                }
            }
            // While searching with no results yet, the gold line above
            // is the whole state — no skeleton, no premature empty state.
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
