package com.sledje.agentpro.feature.enquiries.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** List order (the top bar's sort icon). Remembered per screen (docs/UX.md, Global rule 5). */
enum class EnquirySort(val key: String) { Newest("newest"), Oldest("oldest"), Name("name") }

/** The list after filter, search and sort, plus the chip counts (over all enquiries). */
data class EnquiryListData(
    val rows: List<Enquiry>,
    val counts: Map<EnquiryStatus, Int>,
    val total: Int,
)

/**
 * Enquiries — list (docs/UX.md): status chips from catalog `enquiryStatuses` with counts, search over
 * name, phone, email and message (the SearchField debounces it by 300ms), and a sort. With no enquiries at all the
 * state is [UiState.Empty] ("Share your website to get enquiries"); a filter or search that matches
 * nothing is Content with no rows ("No enquiries match").
 */
class EnquiryListViewModel(
    private val repo: EnquiryRepository,
    private val store: KeyValueStore,
) : ViewModel() {
    private val _filter = MutableStateFlow<EnquiryStatus?>(null)
    val filter: StateFlow<EnquiryStatus?> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(EnquirySort.Newest)
    val sort: StateFlow<EnquirySort> = _sort.asStateFlow()

    private val refreshing = MutableStateFlow(false)

    private val _error = MutableStateFlow<AppError?>(null)
    /** A failed pull-to-refresh, shown once as a snackbar. */
    val error: StateFlow<AppError?> = _error.asStateFlow()

    val state: StateFlow<UiState<EnquiryListData>> = combine(
        repo.observeAll(),
        _filter,
        _query,
        _sort,
        refreshing,
    ) { all, filter, query, sort, refreshing ->
        if (all.isEmpty()) {
            UiState.Empty
        } else {
            val counts = all.groupingBy { it.status }.eachCount()
            UiState.Content(EnquiryListData(arrange(all, filter, query, sort), counts, all.size), refreshing)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    init {
        viewModelScope.launch {
            store.get(FILTER_KEY)?.let { key -> _filter.value = EnquiryStatus.fromKey(key) }
            store.get(SORT_KEY)?.let { key -> EnquirySort.entries.firstOrNull { it.key == key }?.let { _sort.value = it } }
        }
    }

    fun setFilter(status: EnquiryStatus?) {
        _filter.value = status
        viewModelScope.launch { store.put(FILTER_KEY, status?.key) }
    }

    fun setQuery(text: String) { _query.value = text }

    fun setSort(sort: EnquirySort) {
        _sort.value = sort
        viewModelScope.launch { store.put(SORT_KEY, sort.key) }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val result = repo.refresh()
            refreshing.value = false
            if (result is Result.Err && result.error != AppError.Offline) _error.value = result.error
        }
    }

    fun errorShown() { _error.value = null }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        const val FILTER_KEY = "enquiries.filter"
        const val SORT_KEY = "enquiries.sort"

        /** Filter, search and sort (pure; unit-tested). */
        fun arrange(all: List<Enquiry>, filter: EnquiryStatus?, query: String, sort: EnquirySort): List<Enquiry> {
            val q = query.trim()
            val digits = q.filter { it.isDigit() }
            return all
                .filter { filter == null || it.status == filter }
                .filter { e ->
                    q.isEmpty() || e.name.contains(q, ignoreCase = true) ||
                        (e.email?.contains(q, ignoreCase = true) == true) ||
                        (e.message?.contains(q, ignoreCase = true) == true) ||
                        (digits.length >= 3 && e.phone?.filter { it.isDigit() }?.contains(digits) == true)
                }
                .let { list ->
                    when (sort) {
                        EnquirySort.Newest -> list.sortedByDescending { it.createdAt }
                        EnquirySort.Oldest -> list.sortedBy { it.createdAt }
                        EnquirySort.Name -> list.sortedBy { it.name.lowercase() }
                    }
                }
        }
    }
}
