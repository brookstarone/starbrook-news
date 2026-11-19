package com.makrydensystems.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.makrydensystems.news.data.NewsRepository
import com.makrydensystems.news.data.di.AppContainer
import com.makrydensystems.news.data.model.Story
import com.makrydensystems.news.ui.components.EmptyState
import com.makrydensystems.news.ui.components.ErrorState
import com.makrydensystems.news.ui.components.GoldOriginalBadge
import com.makrydensystems.news.ui.components.LiveStarMark
import com.makrydensystems.news.ui.components.SkeletonList
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private sealed interface ReadState {
    data object Loading : ReadState
    data class Found(val story: Story) : ReadState
    data object Missing : ReadState
}

private class ReadViewModel(
    private val repo: NewsRepository,
    private val link: String,
    private val title: String,
) : ViewModel() {
    val state: StateFlow<ReadState> = combine(
        repo.editionsFlow("morning"),
        repo.editionsFlow("evening"),
    ) { morning, evening ->
        val stories = morning + evening
        val found = stories.firstOrNull { it.link == link }
            ?: stories.firstOrNull { it.title.equals(title, ignoreCase = true) }
        when {
            found != null -> ReadState.Found(found)
            // Both editions still empty = still loading, not missing.
            // Editions always carry stories once loaded.
            stories.isEmpty() -> ReadState.Loading
            else -> ReadState.Missing
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState.Loading)

    // Saved state follows the FOUND story's link (breaking/live taps
    // arrive with a read-page URL; the saved row is keyed by the
    // edition link).
    val isSaved: StateFlow<Boolean> = state
        .flatMapLatest { s ->
            val key = (s as? ReadState.Found)?.story?.link ?: link.ifBlank { title }
            repo.isSavedFlow(key)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleSave() {
        viewModelScope.launch {
            val story = (state.value as? ReadState.Found)?.story ?: return@launch
            if (isSaved.value) repo.unsaveStory(story.link) else repo.saveStory(story)
        }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { repo.refreshAll() } }
    }
}

private fun formatStoryTime(publishedMillis: Long): String {
    if (publishedMillis <= 0L) return ""
    val diffMin = (System.currentTimeMillis() - publishedMillis) / 60_000L
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "$diffMin min ago"
        diffMin < 60 * 24 -> "${diffMin / 60}h ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(publishedMillis))
    }
}

/**
 * Native read view (v1): everything the edition JSON carries — title,
 * source + time, hero image, excerpt, Original badge + byline. The
 * full extracted article body lands with the body-extraction step
 * (see data/TODO.md); v1 never opens a WebView.
 */
@Composable
fun ReadScreen(
    container: AppContainer,
    link: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ReadViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ReadViewModel(container.newsRepository, link, title)
            }
        },
    )
    val state by vm.state.collectAsState()
    val isSaved by vm.isSaved.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "‹ Back",
            style = StarBrookType.label,
            color = StarBrookColors.gold,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .clickable(onClick = onBack),
        )
        when (val s = state) {
            is ReadState.Loading -> SkeletonList(count = 3)
            is ReadState.Missing -> ErrorState(
                message = "This story isn't in today's editions yet — it may have just landed. Try again in a moment.",
                onRetry = { vm.refresh() },
            )
            is ReadState.Found -> {
                val story = s.story
                if (story.isOriginal) {
                    GoldOriginalBadge()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = story.title,
                    style = StarBrookType.display,
                    color = StarBrookColors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${story.source} · ${formatStoryTime(story.published)}",
                    style = StarBrookType.caption,
                    color = StarBrookColors.textSecondary,
                )
                if (story.isOriginal) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reported by the StarBrook Newsroom — ${story.beat ?: story.section} Desk. Edited by CADE.",
                        style = StarBrookType.caption,
                        color = StarBrookColors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    LiveStarMark(size = 48.dp)
                    if (!story.image.isNullOrBlank()) {
                        AsyncImage(
                            model = story.image,
                            contentDescription = story.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (!story.excerpt.isNullOrBlank()) {
                    Text(
                        text = story.excerpt,
                        style = StarBrookType.body,
                        color = StarBrookColors.textPrimary,
                    )
                } else {
                    EmptyState(
                        title = "Story summary coming",
                        body = "The full text of this story arrives with the next update.",
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isSaved) "★ Saved to your Shelf" else "☆ Save to your Shelf",
                    style = StarBrookType.label,
                    color = StarBrookColors.gold,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .clickable {
                            scope.launch { vm.toggleSave() }
                        },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
