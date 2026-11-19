package com.makrydensystems.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

private class SavedViewModel(container: AppContainer) : ViewModel() {
    val saved = container.newsRepository.savedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Shelf — local Room cache, renders instantly offline. Signed-in
 * sync lands with the auth step; v1 is local only (free, no login).
 */
@Composable
fun SavedScreen(
    container: AppContainer,
    onStoryClick: (Story) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SavedViewModel = viewModel(
        factory = viewModelFactory { initializer { SavedViewModel(container) } },
    )
    val saved by vm.saved.collectAsState()

    if (saved.isEmpty()) {
        EmptyState(
            title = "Nothing saved yet",
            body = "Tap the bookmark on any story to keep it here.",
        )
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(saved) { story ->
            NewsCard(story = story, onClick = { onStoryClick(story) })
        }
    }
}
