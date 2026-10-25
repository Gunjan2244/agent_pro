package com.sledje.agentpro.feature.customers.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AppFab
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.ConfirmDialog
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.StateCrossfade
import com.sledje.agentpro.core.designsystem.components.FilterChipRow
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.itemMotion
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.OverflowItem
import com.sledje.agentpro.core.designsystem.components.ScreenState
import com.sledje.agentpro.core.designsystem.components.SearchField
import com.sledje.agentpro.core.designsystem.components.SegmentOption
import com.sledje.agentpro.core.designsystem.components.SegmentedControl
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.UrgencyChip
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.core.format.Phone
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.policies.data.Policy
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.activity_customer_added
import com.sledje.agentpro.resources.activity_enquiry
import com.sledje.agentpro.resources.activity_payment
import com.sledje.agentpro.resources.auth_email
import com.sledje.agentpro.resources.cat_field_city
import com.sledje.agentpro.resources.crm_call
import com.sledje.agentpro.resources.crm_delete
import com.sledje.agentpro.resources.crm_dot_join
import com.sledje.agentpro.resources.crm_edit
import com.sledje.agentpro.resources.crm_notes
import com.sledje.agentpro.resources.crm_sms
import com.sledje.agentpro.resources.customer_address
import com.sledje.agentpro.resources.customer_delete_message
import com.sledje.agentpro.resources.customer_delete_message_none
import com.sledje.agentpro.resources.customer_delete_title
import com.sledje.agentpro.resources.customer_dob
import com.sledje.agentpro.resources.customer_no_policy
import com.sledje.agentpro.resources.customer_nominee
import com.sledje.agentpro.resources.customer_not_found
import com.sledje.agentpro.resources.customer_policies_empty
import com.sledje.agentpro.resources.customer_policies_summary
import com.sledje.agentpro.resources.customer_tab_activity
import com.sledje.agentpro.resources.customer_tab_details
import com.sledje.agentpro.resources.customer_tab_policies
import com.sledje.agentpro.resources.customers_add
import com.sledje.agentpro.resources.customers_no_results
import com.sledje.agentpro.resources.ic_add
import com.sledje.agentpro.resources.ic_call
import com.sledje.agentpro.resources.ic_edit
import com.sledje.agentpro.resources.ic_payments
import com.sledje.agentpro.resources.ic_person_add
import com.sledje.agentpro.resources.ic_sms
import com.sledje.agentpro.resources.policy_add
import com.sledje.agentpro.resources.policy_number_short
import com.sledje.agentpro.resources.profile_mobile
import com.sledje.agentpro.resources.setup_customer
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

fun customerRowTag(id: String) = "customer_$id"

/** The Customers segment of the Customers tab (docs/UX.md), wired to its ViewModel. */
@Composable
fun CustomerListScreen(
    vm: CustomerListViewModel,
    onOpen: (String) -> Unit,
    onAddManually: () -> Unit,
    onPickContact: () -> Unit,
    /** Null hides the sheet's "Send the customer a link" option (flag off). */
    onSendLink: (() -> Unit)? = null,
    onOpenIncomplete: () -> Unit = {},
    today: LocalDate = Dates.today(),
) {
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    val incomplete by vm.incompleteCount.collectAsState()
    CustomerListContent(state, filter, vm::setFilter, vm::setQuery, onOpen, onAddManually, onPickContact, onSendLink, incomplete, onOpenIncomplete, today)
}

/** Stateless Customers segment (Incomplete forms row, search, chips, rows, FAB with the Add customer sheet). */
@Composable
fun CustomerListContent(
    state: UiState<CustomerListData>,
    filter: CustomerFilter?,
    onFilter: (CustomerFilter?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAddManually: () -> Unit,
    onPickContact: () -> Unit,
    onSendLink: (() -> Unit)? = null,
    incompleteCount: Int = 0,
    onOpenIncomplete: () -> Unit = {},
    today: LocalDate = Dates.today(),
) {
    var addSheet by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (incompleteCount > 0) IncompleteFormsRow(incompleteCount, onOpenIncomplete)
            if (state is UiState.Content) {
                SearchField(query, { query = it }, onSearch, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
                FilterChipRow(
                    entries = CustomerFilter.entries,
                    selected = filter,
                    onSelect = onFilter,
                    counts = state.data.counts,
                    allCount = state.data.total,
                    modifier = Modifier.padding(vertical = Spacing.s),
                )
            }
            ScreenState(
                state = state,
                empty = {
                    EmptyState(
                        FeatureId.Customers.icon,
                        stringResource(Res.string.setup_customer),
                        actionLabel = stringResource(Res.string.customers_add),
                        onAction = { addSheet = true },
                    )
                },
                onRetry = {},
            ) { data ->
                if (data.rows.isEmpty()) {
                    EmptyState(FeatureId.Customers.icon, stringResource(Res.string.customers_no_results))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(data.rows, key = { _, r -> r.customer.id }) { index, row ->
                            Column(itemMotion()) {
                                if (index > 0) HairlineDivider()
                                CustomerListRow(row, today, onOpen)
                            }
                        }
                    }
                }
            }
        }
        if (state is UiState.Content) {
            AppFab(
                Res.drawable.ic_add,
                stringResource(Res.string.customers_add),
                { addSheet = true },
                Modifier.align(Alignment.BottomEnd).padding(Spacing.m),
            )
        }
    }
    if (addSheet) {
        AppBottomSheet(title = stringResource(Res.string.customers_add), onDismiss = { addSheet = false }) {
            AddCustomerSheetContent(
                onFillMyself = { addSheet = false; onAddManually() },
                onPickContact = { addSheet = false; onPickContact() },
                onSendLink = onSendLink?.let { send -> { addSheet = false; send() } },
            )
        }
    }
}

@Composable
private fun CustomerListRow(row: CustomerRow, today: LocalDate, onOpen: (String) -> Unit) {
    val supporting = if (row.policyCount == 0) {
        stringResource(Res.string.customer_no_policy)
    } else {
        pluralStringResource(Res.plurals.customer_policies_summary, row.policyCount, row.policyCount, Money.format(row.annualPremium))
    }
    ListRow(
        title = row.customer.name,
        supporting = supporting,
        leading = ListLeading.Avatar(row.customer.name),
        onClick = { onOpen(row.customer.id) },
        modifier = Modifier.testTag(customerRowTag(row.customer.id)),
        trailing = row.nextDue?.let { due ->
            @Composable { UrgencyChip(due, today = today) }
        },
    )
}

/** Which tab of the customer detail screen is open. */
enum class CustomerTab(val key: String) { Policies("policies"), Details("details"), Activity("activity") }

/** Customer detail (docs/UX.md), wired to its ViewModel. */
@Composable
fun CustomerDetailScreen(
    vm: CustomerDetailViewModel,
    showPolicies: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddPolicy: (String) -> Unit,
    onOpenPolicy: (String) -> Unit,
    onCall: (String) -> Boolean,
    onSms: (String) -> Boolean,
    /** Extra blocks for the Details tab, e.g. the portal's "Website login" card (`AppFeature.customerDetailItems`). */
    detailExtras: @Composable (customerId: String) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val deleting by vm.deleting.collectAsState()
    val deleted by vm.deleted.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(error) { error?.let { snackbar.showError(it); vm.errorShown() } }
    CustomerDetailContent(
        state = state,
        showPolicies = showPolicies,
        deleting = deleting,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = { onEdit(vm.id) },
        onDelete = vm::delete,
        onAddPolicy = { onAddPolicy(vm.id) },
        onOpenPolicy = onOpenPolicy,
        onCall = { onCall(it) },
        onSms = { onSms(it) },
        detailExtras = { detailExtras(vm.id) },
    )
}

/** Stateless customer detail (also rendered by the screenshot tests). */
@Composable
fun CustomerDetailContent(
    state: CustomerDetailState,
    showPolicies: Boolean,
    deleting: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddPolicy: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    today: LocalDate = Dates.today(),
    initialTab: CustomerTab? = null,
    detailExtras: @Composable () -> Unit = {},
) {
    val online = LocalIsOnline.current
    val tabs = CustomerTab.entries.filter { showPolicies || it != CustomerTab.Policies }
    var tab by rememberSaveable { mutableStateOf((initialTab ?: tabs.first()).key) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shown = state as? CustomerDetailState.Shown
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "",
                onBack = onBack,
                overflow = if (shown != null) listOf(OverflowItem(stringResource(Res.string.crm_delete)) { if (online) confirmDelete = true }) else emptyList(),
            )
        },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            if (shown != null && tab == CustomerTab.Policies.key) {
                BottomActionBar(stringResource(Res.string.policy_add), onAddPolicy, enabled = online, helperText = offlineHelper())
            }
        },
    ) { padding ->
        StateCrossfade(state, key = { it::class }) { s ->
            when (s) {
                CustomerDetailState.Loading -> ListSkeleton(Modifier.padding(padding))
                CustomerDetailState.Gone -> EmptyState(FeatureId.Customers.icon, stringResource(Res.string.customer_not_found), Modifier.padding(padding))
                is CustomerDetailState.Shown -> {
                    val d = s.detail
                    val c = d.customer
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        val line = listOfNotNull(c.phone?.let { Phone.display(it) }, c.city).joinToString(" · ").takeIf { it.isNotEmpty() }
                        DetailHeader(c.name, line)
                        Spacer(Modifier.height(Spacing.m))
                        ContactActionRow(
                            listOf(
                                ContactAction(stringResource(Res.string.crm_call), Res.drawable.ic_call, enabled = c.phone != null) { c.phone?.let(onCall) },
                                ContactAction(stringResource(Res.string.crm_sms), Res.drawable.ic_sms, enabled = c.phone != null) { c.phone?.let(onSms) },
                                ContactAction(stringResource(Res.string.crm_edit), Res.drawable.ic_edit, enabled = online, onClick = onEdit),
                            ),
                        )
                        Spacer(Modifier.height(Spacing.l))
                        SegmentedControl(
                            options = tabs.map { SegmentOption(it.key, stringResource(it.label)) },
                            selectedKey = tab,
                            onSelect = { tab = it },
                            modifier = Modifier.padding(horizontal = Spacing.m),
                        )
                        Spacer(Modifier.height(Spacing.s))
                        StateCrossfade(tab, Modifier.fillMaxSize(), key = { it }) { shownTab ->
                            when (shownTab) {
                                CustomerTab.Policies.key -> PoliciesTab(d.policies, today, onOpenPolicy)
                                CustomerTab.Details.key -> DetailsTab(d, detailExtras)
                                else -> ActivityTab(d.activity)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete && shown != null) {
        val name = shown.detail.customer.name
        val count = shown.detail.policies.size
        ConfirmDialog(
            title = stringResource(Res.string.customer_delete_title),
            message = if (count == 0) {
                stringResource(Res.string.customer_delete_message_none, name)
            } else {
                pluralStringResource(Res.plurals.customer_delete_message, count, name, count)
            },
            confirmLabel = stringResource(Res.string.crm_delete),
            onConfirm = { confirmDelete = false; if (!deleting) onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val CustomerTab.label
    get() = when (this) {
        CustomerTab.Policies -> Res.string.customer_tab_policies
        CustomerTab.Details -> Res.string.customer_tab_details
        CustomerTab.Activity -> Res.string.customer_tab_activity
    }

@Composable
private fun PoliciesTab(policies: List<Policy>, today: LocalDate, onOpen: (String) -> Unit) {
    if (policies.isEmpty()) {
        EmptyState(FeatureId.Policies.icon, stringResource(Res.string.customer_policies_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(policies, key = { _, p -> p.id }) { index, p ->
            Column(itemMotion()) {
                if (index > 0) HairlineDivider()
                PolicyListRow(p, today, onOpen)
            }
        }
    }
}

/** A policy row: plan; "No. 123 · ₹12,000 · Quarterly"; the due chip (or the status when not active). */
@Composable
fun PolicyListRow(p: Policy, today: LocalDate, onOpen: (String) -> Unit) {
    val parts = listOfNotNull(
        p.policyNumber?.let { stringResource(Res.string.policy_number_short, it) },
        p.premiumAmount?.let { Money.format(it) },
        p.premiumMode.label(),
    )
    ListRow(
        title = p.planName ?: p.policyNumber.orEmpty(),
        supporting = parts.joinToString(" · "),
        onClick = { onOpen(p.id) },
        modifier = Modifier.testTag("policy_${p.id}"),
        trailing = {
            val due = p.nextDueDate
            if (p.status == PolicyStatus.Active && due != null) UrgencyChip(due, today = today) else StatusPill(p.status)
        },
    )
}

@Composable
private fun DetailsTab(d: CustomerDetail, extras: @Composable () -> Unit) {
    val c = d.customer
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LabelValue(stringResource(Res.string.profile_mobile), c.phone?.let { Phone.display(it) })
        LabelValue(stringResource(Res.string.auth_email), c.email)
        LabelValue(stringResource(Res.string.customer_dob), c.dob?.let { formatDate(it) })
        LabelValue(stringResource(Res.string.customer_address), c.address)
        LabelValue(stringResource(Res.string.cat_field_city), c.city)
        LabelValue(stringResource(Res.string.customer_nominee), c.nominee)
        LabelValue(stringResource(Res.string.crm_notes), c.notes)
        Spacer(Modifier.height(Spacing.m))
        Column(Modifier.padding(horizontal = Spacing.m)) { extras() }
        Spacer(Modifier.height(Spacing.l))
    }
}

@Composable
private fun ActivityTab(items: List<ActivityItem>) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(items) { index, item ->
            if (index > 0) HairlineDivider()
            val date = formatDate(item.date)
            when (item) {
                is ActivityItem.Payment -> ListRow(
                    title = stringResource(Res.string.activity_payment, Money.format(item.amount)),
                    supporting = item.planName?.let { stringResource(Res.string.crm_dot_join, it, date) } ?: date,
                    leading = ListLeading.Icon(Res.drawable.ic_payments),
                )
                is ActivityItem.FromEnquiry -> ListRow(
                    title = stringResource(Res.string.activity_enquiry, item.interest?.label() ?: date),
                    supporting = date,
                    leading = ListLeading.Icon(FeatureId.Enquiries.icon),
                )
                is ActivityItem.Added -> ListRow(
                    title = stringResource(Res.string.activity_customer_added),
                    supporting = date,
                    leading = ListLeading.Icon(Res.drawable.ic_person_add),
                )
            }
        }
    }
}
