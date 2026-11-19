package com.makrydensystems.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.makrydensystems.news.ui.components.ErrorState
import com.makrydensystems.news.ui.components.NewsCard
import com.makrydensystems.news.ui.components.SkeletonList
import com.makrydensystems.news.ui.theme.StarBrookColors
import com.makrydensystems.news.ui.theme.StarBrookShapes
import com.makrydensystems.news.ui.theme.StarBrookType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class)
private class SearchViewModel(private val repo: NewsRepository) : ViewModel() {
    val query = MutableStateFlow("")
    private val refreshTick = MutableStateFlow(0)

    val results: StateFlow<FeedState<List<Story>>> = query
        .debounce(300)
        .combine(refreshTick) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repo.searchStories(q.trim())
        }
        .asFeedState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

    fun retry() {
        refreshTick.value += 1
    }
}

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

    Column(
        modifier = modifier
            .fillMaxSize()
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
            modifier = Modifier.fillMaxWidth(),
        )
        when (val state = results) {
            is FeedState.Loading -> SkeletonList(count = 3)
            is FeedState.Error -> ErrorState(
                message = state.message,
                onRetry = { vm.retry() },
            )
            is FeedState.Data -> {
                if (query.isBlank()) {
                    EmptyState(
                        title = "Search StarBrook",
                        body = "Try a name, a place, or a topic — results come from today's editions.",
                    )
                } else if (state.value.isEmpty()) {
                    EmptyState(
                        title = "No stories matched \"$query\"",
                        body = "Try different words, or check back when the next edition lands.",
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        items(state.value) { story ->
                            NewsCard(story = story, onClick = { onStoryClick(story) })
                        }
                    }
                }
            }
        }
    }
}
