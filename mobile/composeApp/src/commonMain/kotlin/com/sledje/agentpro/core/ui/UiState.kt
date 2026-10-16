package com.sledje.agentpro.core.ui

import org.jetbrains.compose.resources.StringResource

/**
 * What a data screen shows (docs/UX.md, Global UX rule 1). ViewModels expose a single
 * `StateFlow<UiState<T>>`; the `ScreenState` component in core/designsystem renders it.
 */
sealed interface UiState<out T> {
    /** First load: a skeleton is shown. */
    data object Loading : UiState<Nothing>

    /** Loaded, nothing to show: one sentence and one button. */
    data object Empty : UiState<Nothing>

    /** Failed: a message and Retry. [message] is null for the generic "Something went wrong". */
    data class Error(val message: StringResource? = null) : UiState<Nothing>

    /** Loaded. [refreshing] is true during pull-to-refresh. */
    data class Content<T>(val data: T, val refreshing: Boolean = false) : UiState<T>
}

/** The four screen states, for code that only needs to know which one is showing. */
enum class ScreenState { Loading, Empty, Error, Content }

val UiState<*>.screenState: ScreenState
    get() = when (this) {
        UiState.Loading -> ScreenState.Loading
        UiState.Empty -> ScreenState.Empty
        is UiState.Error -> ScreenState.Error
        is UiState.Content -> ScreenState.Content
    }

/** Content when the data is non-empty, otherwise [UiState.Empty]. */
fun <T> List<T>.toUiState(refreshing: Boolean = false): UiState<List<T>> =
    if (isEmpty()) UiState.Empty else UiState.Content(this, refreshing)
