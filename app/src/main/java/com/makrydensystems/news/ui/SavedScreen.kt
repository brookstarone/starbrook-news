package com.makrydensystems.news.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.NewsCard
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class SavedViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.newsRepository

    val saved = repo.savedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val refreshing = MutableStateFlow(false)

    /** Unsave from the Shelf itself. The Room flow re-emits and the
     * row leaves the list; the edition cache is untouched. */
    fun unsave(link: String) {
        viewModelScope.launch { repo.unsaveStory(link) }
    }

    /**
     * Pull-to-refresh re-syncs the edition cache the Shelf opens into
     * (a saved story opens through the read view, which reads today's
     * editions). The saved rows themselves are local Room and never
     * leave the device. Best-effort — offline just leaves the Shelf
     * as it is.
     */
    fun refresh() {
        viewModelScope.launch {
            if (refreshing.value) return@launch
            refreshing.value = true
            try {
                runCatching { repo.refreshAll() }
            } finally {
                refreshing.value = false
            }
        }
    }
}

/**
 * Shelf — local Room cache, renders instantly offline. Signed-in
 * sync lands with the auth step; v1 is local only (free, no login).
 *
 * LIST DIAGNOSIS (2026-11-19): the list wiring itself traces clean —
 * [com.makrydensystems.news.data.NewsRepository.savedFlow] reads the
 * `saved_stories` Room table and the LazyColumn renders it, and
 * [com.makrydensystems.news.data.NewsRepository.saveStory] writes it
 * from the read view. What made the Shelf read empty in practice:
 * the empty state promised "Tap the bookmark on any story", but no
 * story card in the app has a bookmark — the only save control is
 * "Save to your Shelf" at the bottom of the read view. The copy below
 * now describes the real path. (Opening a saved story is the
 * ReadViewModel's job — it can only find stories still in today's
 * editions; the Shelf passes the saved item's URL through unchanged,
 * see MainActivity's `story?link=` navigation, which already encodes
 * the saved story's link + title.)
 *
 * [signedIn] is the auth wiring point (TODO.md §2/§3): no sign-in
 * exists in v1, so it defaults to false and the guest-saves line
 * shows. When auth lands, MainActivity passes the real session state
 * and the line hides for signed-in readers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    container: AppContainer,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
    signedIn: Boolean = false,
) {
    val vm: SavedViewModel = viewModel(
        factory = viewModelFactory { initializer { SavedViewModel(container) } },
    )
    val saved by vm.saved.collectAsState()
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
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Guest saves live on this device only until sign-in.
            // Shown while signed out; hidden once auth lands and the
            // caller passes signedIn = true.
            if (!signedIn) {
                item {
                    Text(
                        text = "Saved on this device only until you sign in.",
                        style = StarBrookType.caption,
                        color = StarBrookColors.textSecondary,
                    )
                }
            }
            if (saved.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing saved yet",
                        body = "Open any story, then tap 'Save to your Shelf' at the bottom of the article to keep it here.",
                    )
                }
            } else {
                items(saved, key = { it.link }) { story ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NewsCard(story = story, onClick = { onStoryClick(story) }, outletCount = story.outletCount)
                        OutlinedButton(
                            onClick = { vm.unsave(story.link) },
                            shape = StarBrookShapes.chip,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StarBrookColors.gold,
                            ),
                            border = BorderStroke(1.dp, StarBrookColors.gold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(text = "Remove from Shelf", style = StarBrookType.label)
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
