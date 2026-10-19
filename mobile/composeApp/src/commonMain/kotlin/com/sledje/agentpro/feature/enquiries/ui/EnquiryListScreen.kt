package com.sledje.agentpro.feature.enquiries.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AppFab
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.FilterChipRow
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.itemMotion
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ScreenState
import com.sledje.agentpro.core.designsystem.components.SearchField
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.TopBarAction
import com.sledje.agentpro.core.format.relativeTime
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.ui.showError
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.crm_dot_join
import com.sledje.agentpro.resources.crm_sort
import com.sledje.agentpro.resources.crm_sort_title
import com.sledje.agentpro.resources.enquiries_empty
import com.sledje.agentpro.resources.enquiries_no_results
import com.sledje.agentpro.resources.enquiries_share_link
import com.sledje.agentpro.resources.enquiry_add
import com.sledje.agentpro.resources.enquiry_sort_name
import com.sledje.agentpro.resources.enquiry_sort_newest
import com.sledje.agentpro.resources.enquiry_sort_oldest
import com.sledje.agentpro.resources.enquiry_unseen
import com.sledje.agentpro.resources.ic_add
import com.sledje.agentpro.resources.ic_check
import com.sledje.agentpro.resources.ic_search
import com.sledje.agentpro.resources.ic_sort
import com.sledje.agentpro.resources.search_hint
import com.sledje.agentpro.resources.tab_enquiries
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Test tag of an enquiry row: `enquiry_<id>`. */
fun enquiryRowTag(id: String) = "enquiry_$id"

/** The Enquiries tab (docs/UX.md, Enquiries — list), wired to its ViewModel. */
@Composable
fun EnquiryListScreen(
    vm: EnquiryListViewModel,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onShareLink: (() -> Unit)?,
) {
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbar.showError(it); vm.errorShown() }
    }
    EnquiryListContent(
        state = state,
        filter = filter,
        sort = sort,
        onFilter = vm::setFilter,
        onSearch = vm::setQuery,
        onSort = vm::setSort,
        onRefresh = vm::refresh,
        onOpen = onOpen,
        onAdd = onAdd,
        onShareLink = onShareLink,
        snackbar = snackbar,
    )
}

/** Stateless list screen (also rendered by the screenshot tests). */
@Composable
fun EnquiryListContent(
    state: UiState<EnquiryListData>,
    filter: EnquiryStatus?,
    sort: EnquirySort,
    onFilter: (EnquiryStatus?) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (EnquirySort) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onShareLink: (() -> Unit)?,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    now: Instant = Clock.System.now(),
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    val hasData = state is UiState.Content
    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.tab_enquiries),
                actions = if (hasData) {
                    listOf(
                        TopBarAction(Res.drawable.ic_search, stringResource(Res.string.search_hint)) {
                            searchOpen = !searchOpen
                            if (!searchOpen) { query = ""; onSearch("") }
                        },
                        TopBarAction(Res.drawable.ic_sort, stringResource(Res.string.crm_sort)) { sortOpen = true },
                    )
                } else {
                    emptyList()
                },
            )
        },
        offline = !LocalIsOnline.current,
        snackbarHostState = snackbar,
        floatingActionButton = { AppFab(Res.drawable.ic_add, stringResource(Res.string.enquiry_add), onAdd) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (hasData) {
                if (searchOpen) {
                    SearchField(query, { query = it }, onSearch, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
                }
                val data = (state as UiState.Content).data
                FilterChipRow(
                    entries = EnquiryStatus.active,
                    selected = filter,
                    onSelect = onFilter,
                    counts = EnquiryStatus.active.associateWith { data.counts[it] ?: 0 },
                    allCount = data.total,
                    modifier = Modifier.padding(vertical = Spacing.s),
                )
            }
            ScreenState(
                state = state,
                empty = {
                    EmptyState(
                        FeatureId.Enquiries.icon,
                        stringResource(Res.string.enquiries_empty),
                        actionLabel = onShareLink?.let { stringResource(Res.string.enquiries_share_link) },
                        onAction = onShareLink,
                    )
                },
                onRetry = onRefresh,
                onRefresh = onRefresh,
            ) { data ->
                if (data.rows.isEmpty()) {
                    EmptyState(FeatureId.Enquiries.icon, stringResource(Res.string.enquiries_no_results))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(data.rows, key = { _, e -> e.id }) { index, e ->
                            Column(itemMotion()) {
                                if (index > 0) HairlineDivider()
                                EnquiryRow(e, now, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
    if (sortOpen) {
        AppBottomSheet(title = stringResource(Res.string.crm_sort_title), onDismiss = { sortOpen = false }) {
            EnquirySort.entries.forEach { option ->
                ListRow(
                    title = stringResource(option.label),
                    selected = option == sort,
                    onClick = { onSort(option); sortOpen = false },
                    trailing = if (option == sort) {
                        { Icon(painterResource(Res.drawable.ic_check), null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private val EnquirySort.label
    get() = when (this) {
        EnquirySort.Newest -> Res.string.enquiry_sort_newest
        EnquirySort.Oldest -> Res.string.enquiry_sort_oldest
        EnquirySort.Name -> Res.string.enquiry_sort_name
    }

@Composable
private fun EnquiryRow(e: Enquiry, now: Instant, onOpen: (String) -> Unit) {
    val time = relativeTime(e.createdAt, now)
    val supporting = e.interest?.let { stringResource(Res.string.crm_dot_join, it.label(), time) } ?: time
    ListRow(
        title = e.name,
        supporting = supporting,
        onClick = { onOpen(e.id) },
        unseenLabel = if (e.unseen) stringResource(Res.string.enquiry_unseen) else null,
        modifier = Modifier.testTag(enquiryRowTag(e.id)),
        trailing = { StatusPill(e.status) },
    )
}
