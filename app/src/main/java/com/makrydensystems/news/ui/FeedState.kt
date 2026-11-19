package com.makrydensystems.news.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * One feed, three states. Loading shows only while the cache is empty
 * (cached data renders instantly per the INSTANT STANDARD); an error
 * never hides data that is already on screen — the repository keeps
 * serving the cache and the screen shows its own retry.
 */
sealed interface FeedState<out T> {
    data object Loading : FeedState<Nothing>
    data class Data<T>(val value: T) : FeedState<T>
    data class Error(val message: String) : FeedState<Nothing>
}

fun <T> Flow<T>.asFeedState(): Flow<FeedState<T>> =
    map<T, FeedState<T>> { FeedState.Data(it) }
        .catch {
            emit(
                FeedState.Error(
                    "Couldn't load this — check your connection and try again.",
                ),
            )
        }
