package com.sledje.agentpro.feature.dues.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.FilterChipRow
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.itemMotion
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ScreenState
import com.sledje.agentpro.core.designsystem.components.UrgencyChip
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.domain.DueBuckets
import com.sledje.agentpro.feature.customers.data.Customer
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.policies.data.Policy
import com.sledje.agentpro.feature.policies.data.PolicyRepository
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.crm_call_person
import com.sledje.agentpro.resources.crm_dot_join
import com.sledje.agentpro.resources.dues_empty
import com.sledje.agentpro.resources.dues_summary
import com.sledje.agentpro.resources.ic_call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One due premium: an active policy whose next due date falls in a catalog due bucket. */
data class DueRow(val policy: Policy, val customer: Customer?, val due: LocalDate, val bucket: DueBucket)

/** The rows of one due date (rows are grouped by date; docs/UX.md, Dues). */
data class DueGroup(val date: LocalDate, val rows: List<DueRow>)

data class DuesData(
    val groups: List<DueGroup>,
    /** "₹1,24,500 due · 9 policies" for the selected chip. */
    val total: Double,
    val count: Int,
    val counts: Map<DueBucket, Int>,
    val allCount: Int,
)

/**
 * The Dues segment (docs/UX.md, Customers tab → Dues): chips from catalog `dueBuckets` with counts
 * (Overdue · This week · This month · Next 3 months; "All" is every bucket), one summary line, and
 * rows grouped by date. Only **active** policies with a next due date inside the buckets count.
 */
class DuesViewModel(
    policies: PolicyRepository,
    customers: CustomerRepository,
    private val store: KeyValueStore,
    private val today: () -> LocalDate,
    initialBucket: String? = null,
) : ViewModel() {
    private val _bucket = MutableStateFlow(initialBucket?.let { DueBucket.fromKey(it) })
    val bucket: StateFlow<DueBucket?> = _bucket.asStateFlow()

    val state: StateFlow<UiState<DuesData>> = combine(policies.observeAll(), customers.observeAll(), _bucket) { all, people, bucket ->
        val rows = dueRows(all, people, today())
        if (rows.isEmpty()) UiState.Empty else UiState.Content(summarise(rows, bucket))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        if (initialBucket == null) {
            viewModelScope.launch { store.get(BUCKET_KEY)?.let { _bucket.value = DueBucket.fromKey(it) } }
        }
    }

    fun setBucket(b: DueBucket?) {
        _bucket.value = b
        viewModelScope.launch { store.put(BUCKET_KEY, b?.key) }
    }

    companion object {
        const val BUCKET_KEY = "dues.bucket"

        fun dueRows(policies: List<Policy>, customers: List<Customer>, today: LocalDate): List<DueRow> {
            val people = customers.associateBy { it.id }
            return policies
                .filter { it.status == PolicyStatus.Active && it.customerId in people }
                .mapNotNull { p ->
                    val due = p.nextDueDate ?: return@mapNotNull null
                    val bucket = DueBuckets.bucketOf(due, today) ?: return@mapNotNull null
                    DueRow(p, people[p.customerId], due, bucket)
                }
                .sortedWith(compareBy<DueRow> { it.due }.thenBy { it.customer?.name?.lowercase() })
        }

        fun summarise(rows: List<DueRow>, bucket: DueBucket?): DuesData {
            val shown = rows.filter { bucket == null || it.bucket == bucket }
            return DuesData(
                groups = shown.groupBy { it.due }.map { (d, r) -> DueGroup(d, r) },
                total = shown.sumOf { it.policy.premiumAmount ?: 0.0 },
                count = shown.size,
                counts = DueBucket.active.associateWith { b -> rows.count { it.bucket == b } },
                allCount = rows.size,
            )
        }
    }
}

@Composable
fun DuesScreen(vm: DuesViewModel, onOpenPolicy: (String) -> Unit, onCall: (String) -> Unit, today: LocalDate = Dates.today()) {
    val state by vm.state.collectAsState()
    val bucket by vm.bucket.collectAsState()
    DuesContent(state, bucket, vm::setBucket, onOpenPolicy, onCall, today)
}

/** Stateless Dues segment (also rendered by the screenshot tests). */
@Composable
fun DuesContent(
    state: UiState<DuesData>,
    bucket: DueBucket?,
    onBucket: (DueBucket?) -> Unit,
    onOpenPolicy: (String) -> Unit,
    onCall: (String) -> Unit,
    today: LocalDate = Dates.today(),
) {
    Column(Modifier.fillMaxSize()) {
        if (state is UiState.Content) {
            FilterChipRow(
                entries = DueBucket.active,
                selected = bucket,
                onSelect = onBucket,
                counts = state.data.counts,
                allCount = state.data.allCount,
                modifier = Modifier.padding(vertical = Spacing.s),
            )
        }
        ScreenState(
            state = state,
            empty = { EmptyState(FeatureId.Dues.icon, stringResource(Res.string.dues_empty)) },
            onRetry = {},
        ) { data ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        pluralStringResource(Res.plurals.dues_summary, data.count, Money.format(data.total), data.count),
                        style = AgentProTheme.text.strong,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
                if (data.groups.isEmpty()) {
                    item { EmptyState(FeatureId.Dues.icon, stringResource(Res.string.dues_empty)) }
                }
                data.groups.forEach { group ->
                    item(key = "date_${group.date}") { Box(itemMotion()) { DueDateHeader(group.date, today) } }
                    itemsIndexed(group.rows, key = { _, r -> r.policy.id }) { index, row ->
                        Column(itemMotion()) {
                            if (index > 0) HairlineDivider()
                            DueListRow(row, onOpenPolicy, onCall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A date group's header: the date and its `UrgencyChip`. The chip sits here rather than on each row,
 * so every row keeps a single trailing element (the Call icon; calm-screen rule 7).
 */
@Composable
private fun DueDateHeader(date: LocalDate, today: LocalDate) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(formatDate(date), style = AgentProTheme.text.label, modifier = Modifier.weight(1f))
        UrgencyChip(date, today = today)
    }
}

@Composable
private fun DueListRow(row: DueRow, onOpenPolicy: (String) -> Unit, onCall: (String) -> Unit) {
    val name = row.customer?.name.orEmpty()
    val plan = row.policy.planName ?: row.policy.policyNumber.orEmpty()
    val amount = Money.format(row.policy.premiumAmount ?: 0.0)
    val phone = row.customer?.phone
    ListRow(
        title = name,
        supporting = stringResource(Res.string.crm_dot_join, plan, amount),
        onClick = { onOpenPolicy(row.policy.id) },
        modifier = Modifier.testTag("due_${row.policy.id}"),
        trailing = if (phone != null) {
            {
                IconButton(onClick = { onCall(phone) }) {
                    Icon(painterResource(Res.drawable.ic_call), stringResource(Res.string.crm_call_person, name))
                }
            }
        } else {
            null
        },
    )
}
