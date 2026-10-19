package com.sledje.agentpro.feature.policies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.StateCrossfade
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.OverflowItem
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.SectionHeader
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.UrgencyChip
import com.sledje.agentpro.core.designsystem.components.showUndo
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.customers.ui.CrmFormScreen
import com.sledje.agentpro.feature.customers.ui.DetailHeader
import com.sledje.agentpro.feature.customers.ui.LabelValue
import com.sledje.agentpro.feature.customers.ui.offlineHelper
import com.sledje.agentpro.feature.customers.ui.showError
import com.sledje.agentpro.feature.policies.data.Policy
import com.sledje.agentpro.feature.policies.data.PremiumPayment
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_undo
import com.sledje.agentpro.resources.crm_edit
import com.sledje.agentpro.resources.crm_notes
import com.sledje.agentpro.resources.payment_next_due
import com.sledje.agentpro.resources.payment_record
import com.sledje.agentpro.resources.payment_recorded
import com.sledje.agentpro.resources.payment_supporting
import com.sledje.agentpro.resources.payment_title
import com.sledje.agentpro.resources.payment_undo_failed
import com.sledje.agentpro.resources.policy_customer
import com.sledje.agentpro.resources.policy_details
import com.sledje.agentpro.resources.policy_form_add
import com.sledje.agentpro.resources.policy_form_edit
import com.sledje.agentpro.resources.policy_mode
import com.sledje.agentpro.resources.policy_next_due
import com.sledje.agentpro.resources.policy_no_due
import com.sledje.agentpro.resources.policy_not_found
import com.sledje.agentpro.resources.policy_number
import com.sledje.agentpro.resources.policy_payments
import com.sledje.agentpro.resources.policy_payments_empty
import com.sledje.agentpro.resources.policy_premium
import com.sledje.agentpro.resources.policy_record
import com.sledje.agentpro.resources.policy_start
import com.sledje.agentpro.resources.policy_status
import com.sledje.agentpro.resources.policy_sum_assured
import com.sledje.agentpro.resources.policy_summary
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Test tag of the record-payment sheet's Record button. */
const val RECORD_BUTTON_TAG = "payment_record"

/** The policy form (add or edit), wired to its ViewModel. [onSaved] gets the policy's id. */
@Composable
fun PolicyFormScreen(vm: PolicyFormViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val form by vm.form.collectAsState()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showError(it) } }
    val title = stringResource(if (vm.policyId == null) Res.string.policy_form_add else Res.string.policy_form_edit)
    val f = form
    if (f == null) {
        AppScaffold(topBar = { AppTopBar(title = title, onBack = onBack) }) { ListSkeleton() }
        return
    }
    CrmFormScreen(
        title = title,
        form = f,
        saving = state.saving,
        onSave = vm::save,
        onBack = onBack,
        snackbar = snackbar,
        visibleKeys = PolicyFormViewModel.VISIBLE,
        aboveSave = vm.summary(f)?.let { s -> @Composable { PolicySummaryLine(s) } },
    )
}

/** "₹12,000 · Quarterly · next due 15 Jan 2027". */
@Composable
fun PolicySummaryLine(s: PolicySummary) {
    Text(
        stringResource(Res.string.policy_summary, Money.format(s.premium), s.mode.label(), formatDate(s.nextDue)),
        style = AgentProTheme.text.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Policy detail (docs/UX.md), wired to its ViewModel. */
@Composable
fun PolicyDetailScreen(
    vm: PolicyDetailViewModel,
    onBack: () -> Unit,
    onEdit: (customerId: String, policyId: String) -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val recording by vm.recording.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(false) }
    val undoLabel = stringResource(Res.string.action_undo)
    // One long-lived collector: consuming the event must not cancel the snackbar it shows.
    LaunchedEffect(vm) {
        vm.event.filterNotNull().collect { e ->
            vm.eventShown()
            when (e) {
                is PaymentEvent.Recorded -> {
                    sheet = false
                    if (snackbar.showUndo(getString(Res.string.payment_recorded), undoLabel)) vm.undo(e)
                }
                is PaymentEvent.Failed -> snackbar.showError(e.error)
                PaymentEvent.UndoRefused -> snackbar.showSnackbar(getString(Res.string.payment_undo_failed))
            }
        }
    }
    PolicyDetailContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = { p -> onEdit(p.customerId, p.id) },
        onOpenCustomer = onOpenCustomer,
        onRecord = { sheet = true },
    )
    val shown = state as? PolicyDetailState.Shown
    if (sheet && shown != null) {
        val today = Dates.today()
        val form = remember(shown.policy.id) { PolicyDetailViewModel.paymentForm(shown.policy, today) }
        AppBottomSheet(title = stringResource(Res.string.payment_title), onDismiss = { if (!recording) sheet = false }) {
            RecordPaymentSheetContent(
                policy = shown.policy,
                form = form,
                recording = recording,
                onRecord = { amount, paidOn -> vm.record(amount, paidOn) },
            )
        }
    }
}

/**
 * The record-payment sheet's content (docs/UX.md, Policy detail): amount (prefilled) and date
 * (today), "Next due becomes 15 Apr 2027", and **Record**.
 */
@Composable
fun RecordPaymentSheetContent(
    policy: Policy,
    form: FormState,
    recording: Boolean,
    onRecord: (Double, LocalDate) -> Unit,
) {
    val online = LocalIsOnline.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        FormRenderer(form, enabled = !recording)
        PolicyDetailViewModel.nextDueAfterPayment(policy)?.let { next ->
            Text(stringResource(Res.string.payment_next_due, formatDate(next)), style = AgentProTheme.text.body)
        }
        PrimaryButton(
            stringResource(Res.string.payment_record),
            {
                if (form.validateAll()) {
                    val v = form.values()
                    onRecord(v[PolicyDetailViewModel.AMOUNT] as Double, v[PolicyDetailViewModel.PAID_ON] as LocalDate)
                }
            },
            Modifier.fillMaxWidth().testTag(RECORD_BUTTON_TAG),
            enabled = online && !recording,
        )
        offlineHelper()?.let { Text(it, style = AgentProTheme.text.bodyMuted) }
    }
}

/** Stateless policy detail (summary, payment history, Record premium paid). */
@Composable
fun PolicyDetailContent(
    state: PolicyDetailState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (Policy) -> Unit,
    onOpenCustomer: (String) -> Unit,
    onRecord: () -> Unit,
    today: LocalDate = Dates.today(),
) {
    val online = LocalIsOnline.current
    val shown = state as? PolicyDetailState.Shown
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "",
                onBack = onBack,
                overflow = if (shown != null) listOf(OverflowItem(stringResource(Res.string.crm_edit)) { if (online) onEdit(shown.policy) }) else emptyList(),
            )
        },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            if (shown != null) {
                val hasDue = shown.policy.nextDueDate != null
                BottomActionBar(
                    stringResource(Res.string.policy_record),
                    onRecord,
                    enabled = online && hasDue,
                    helperText = offlineHelper() ?: if (!hasDue) stringResource(Res.string.policy_no_due) else null,
                )
            }
        },
    ) { padding ->
        StateCrossfade(state, key = { it::class }) { s ->
            when (s) {
                PolicyDetailState.Loading -> ListSkeleton(Modifier.padding(padding))
                PolicyDetailState.Gone -> EmptyState(FeatureId.Policies.icon, stringResource(Res.string.policy_not_found), Modifier.padding(padding))
                is PolicyDetailState.Shown -> PolicyDetailBody(s, today, onOpenCustomer, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun PolicyDetailBody(s: PolicyDetailState.Shown, today: LocalDate, onOpenCustomer: (String) -> Unit, modifier: Modifier) {
    val p = s.policy
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHeader(
            p.planName ?: p.policyNumber.orEmpty(),
            s.customer?.name,
            trailing = {
                val due = p.nextDueDate
                if (p.status == PolicyStatus.Active && due != null) UrgencyChip(due, today = today) else StatusPill(p.status)
            },
        )
        SectionHeader(stringResource(Res.string.policy_details))
        s.customer?.let { c ->
            ListRow(title = c.name, supporting = stringResource(Res.string.policy_customer), onClick = { onOpenCustomer(c.id) })
        }
        LabelValue(stringResource(Res.string.policy_number), p.policyNumber)
        LabelValue(stringResource(Res.string.policy_premium), p.premiumAmount?.let { Money.format(it) })
        LabelValue(stringResource(Res.string.policy_mode), p.premiumMode.label())
        LabelValue(stringResource(Res.string.policy_next_due), p.nextDueDate?.let { formatDate(it) })
        LabelValue(stringResource(Res.string.policy_status), p.status.label())
        LabelValue(stringResource(Res.string.policy_start), p.startDate?.let { formatDate(it) })
        LabelValue(stringResource(Res.string.policy_sum_assured), p.sumAssured?.let { Money.format(it) })
        LabelValue(stringResource(Res.string.crm_notes), p.notes)
        SectionHeader(stringResource(Res.string.policy_payments))
        if (s.payments.isEmpty()) {
            Text(
                stringResource(Res.string.policy_payments_empty),
                style = AgentProTheme.text.bodyMuted,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        } else {
            s.payments.forEachIndexed { index, pay -> if (index > 0) HairlineDivider(); PaymentRow(pay) }
        }
        Spacer(Modifier.height(Spacing.l))
    }
}

@Composable
private fun PaymentRow(pay: PremiumPayment) {
    ListRow(
        title = Money.format(pay.amount),
        supporting = stringResource(Res.string.payment_supporting, formatDate(pay.paidOn), formatDate(pay.periodDueDate)),
    )
}
