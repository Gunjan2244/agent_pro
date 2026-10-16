package com.sledje.agentpro.feature.policies.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.PlanSuggestions
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.parseDateOrNull
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.core.forms.FieldDefault
import com.sledje.agentpro.core.forms.FieldOption
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.domain.DueDateCalculator
import com.sledje.agentpro.feature.customers.data.Customer
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.enquiries.ui.SaveState
import com.sledje.agentpro.feature.policies.data.MarkPaidResult
import com.sledje.agentpro.feature.policies.data.Policy
import com.sledje.agentpro.feature.policies.data.PolicyInput
import com.sledje.agentpro.feature.policies.data.PolicyRepository
import com.sledje.agentpro.feature.policies.data.PremiumPayment
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.crm_notes
import com.sledje.agentpro.resources.payment_amount
import com.sledje.agentpro.resources.payment_date
import com.sledje.agentpro.resources.policy_mode
import com.sledje.agentpro.resources.policy_next_due
import com.sledje.agentpro.resources.policy_number
import com.sledje.agentpro.resources.policy_plan
import com.sledje.agentpro.resources.policy_premium
import com.sledje.agentpro.resources.policy_start
import com.sledje.agentpro.resources.policy_status
import com.sledje.agentpro.resources.policy_sum_assured
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** The live summary above Save: "₹12,000 · Quarterly · next due 15 Jan 2027". */
data class PolicySummary(val premium: Double, val mode: PremiumMode, val nextDue: LocalDate)

/**
 * The policy form (docs/UX.md, Policy form). Always shown: plan name (autocomplete from catalog
 * `planSuggestions`), policy number, premium, mode (segmented control from `premiumModes`) and start
 * date. Under More details: sum assured, a next-due override, status (`policyStatuses`) and notes. When
 * the override is empty the next due date is `DueDateCalculator.initialNextDue(start, mode, today)`.
 */
class PolicyFormViewModel(
    val customerId: String,
    val policyId: String?,
    private val repo: PolicyRepository,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val _form = MutableStateFlow<FormState?>(null)
    val form: StateFlow<FormState?> = _form.asStateFlow()

    private val _state = MutableStateFlow(SaveState())
    val state: StateFlow<SaveState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = policyId?.let { repo.observe(it).first() }
            val initial: Map<String, Any?> = existing?.let { p ->
                mapOf(
                    PLAN to p.planName, NUMBER to p.policyNumber, PREMIUM to p.premiumAmount?.let(::amountText),
                    MODE to p.premiumMode.key, START to p.startDate, SUM_ASSURED to p.sumAssured?.let(::amountText),
                    NEXT_DUE to p.nextDueDate, STATUS to p.status.key, NOTES to p.notes,
                )
            }.orEmpty()
            _form.value = FormState(FIELDS, initial.filterValues { it != null })
        }
    }

    /** The summary while the required fields allow one, else null (shown live above Save). */
    fun summary(form: FormState): PolicySummary? {
        val premium = Money.parse(form.text(PREMIUM)) ?: return null
        val mode = PremiumMode.fromKey(form.text(MODE)) ?: return null
        val start = form.value(START) as? LocalDate ?: return null
        val next = form.value(NEXT_DUE) as? LocalDate ?: DueDateCalculator.initialNextDue(start, mode, today())
        return PolicySummary(premium, mode, next)
    }

    fun save() {
        val form = _form.value ?: return
        if (_state.value.saving || !form.validateAll()) return
        val v = form.values()
        val mode = PremiumMode.fromKey(v[MODE] as String) ?: return
        val start = v[START] as LocalDate
        val input = PolicyInput(
            customerId = customerId,
            planName = v[PLAN] as String,
            policyNumber = v[NUMBER] as String,
            premiumAmount = v[PREMIUM] as Double,
            premiumMode = mode,
            startDate = start,
            nextDueDate = v[NEXT_DUE] as LocalDate? ?: DueDateCalculator.initialNextDue(start, mode, today()),
            status = (v[STATUS] as String?)?.let { PolicyStatus.fromKey(it) } ?: PolicyStatus.Active,
            sumAssured = v[SUM_ASSURED] as Double?,
            notes = v[NOTES] as String?,
        )
        _state.value = SaveState(saving = true)
        viewModelScope.launch {
            val result = if (policyId == null) repo.create(input) else repo.update(policyId, input)
            _state.value = when (result) {
                is Result.Ok -> SaveState(savedId = result.value.id)
                is Result.Err -> SaveState(error = result.error)
            }
        }
    }

    companion object {
        const val PLAN = "plan_name"
        const val NUMBER = "policy_number"
        const val PREMIUM = "premium_amount"
        const val MODE = "premium_mode"
        const val START = "start_date"
        const val SUM_ASSURED = "sum_assured"
        const val NEXT_DUE = "next_due_date"
        const val STATUS = "status"
        const val NOTES = "notes"

        /** The column limit `numeric(12,2)` (premium) and `numeric(14,2)` (sum assured), in whole rupees. */
        private const val MAX_PREMIUM = 9_999_999_999L
        private const val MAX_SUM_ASSURED = 999_999_999_999L

        val FIELDS: List<FieldSpec> = listOf(
            FieldSpec(PLAN, FieldType.Text, Res.string.policy_plan, required = true, maxLength = 80, suggestions = PlanSuggestions.all),
            FieldSpec(NUMBER, FieldType.Text, Res.string.policy_number, required = true, maxLength = 30),
            FieldSpec(PREMIUM, FieldType.Money, Res.string.policy_premium, required = true, min = 1, max = MAX_PREMIUM),
            FieldSpec(
                MODE, FieldType.Select, Res.string.policy_mode, required = true, segmented = true,
                options = PremiumMode.active.map { FieldOption(it.key, it.label, it.icon) },
                default = FieldDefault.Text(PremiumMode.active.first().key),
            ),
            FieldSpec(START, FieldType.Date, Res.string.policy_start, required = true),
            FieldSpec(SUM_ASSURED, FieldType.Money, Res.string.policy_sum_assured, min = 1, max = MAX_SUM_ASSURED),
            FieldSpec(NEXT_DUE, FieldType.Date, Res.string.policy_next_due),
            FieldSpec(
                // Not starred (under More details); blank falls back to Active on save.
                STATUS, FieldType.Select, Res.string.policy_status,
                options = PolicyStatus.active.map { FieldOption(it.key, it.label, it.icon) },
                default = FieldDefault.Text(PolicyStatus.Active.key),
            ),
            FieldSpec(NOTES, FieldType.Multiline, Res.string.crm_notes, maxLength = 500),
        )

        /** The five starred fields; sum assured, next-due override, status and notes go under More details. */
        val VISIBLE = setOf(PLAN, NUMBER, PREMIUM, MODE, START)

        /** "12000" / "12000.5" for prefilling a Money field. */
        fun amountText(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}

sealed interface PolicyDetailState {
    data object Loading : PolicyDetailState
    data object Gone : PolicyDetailState
    data class Shown(val policy: Policy, val customer: Customer?, val payments: List<PremiumPayment>) : PolicyDetailState
}

/** One-shot results of recording a premium. */
sealed interface PaymentEvent {
    /** Recorded; the snackbar offers Undo for [result]'s payment. */
    data class Recorded(val result: MarkPaidResult) : PaymentEvent
    data class Failed(val error: AppError) : PaymentEvent
    /** Undo was refused because a newer payment exists (DECISIONS #31). */
    data object UndoRefused : PaymentEvent
}

/**
 * Policy detail (docs/UX.md): summary, payment history and **Record premium paid** →
 * `mark_premium_paid` → snackbar with Undo → `undo_premium_payment` (latest payment only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PolicyDetailViewModel(
    val id: String,
    private val repo: PolicyRepository,
    customers: CustomerRepository,
) : ViewModel() {
    private val policy = repo.observe(id)

    val state: StateFlow<PolicyDetailState> = combine(
        policy,
        policy.flatMapLatest { p -> p?.customerId?.let { customers.observe(it) } ?: flowOf(null) },
        repo.observePayments(id),
    ) { p, c, payments ->
        if (p == null) PolicyDetailState.Gone else PolicyDetailState.Shown(p, c, payments)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PolicyDetailState.Loading)

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _event = MutableStateFlow<PaymentEvent?>(null)
    val event: StateFlow<PaymentEvent?> = _event.asStateFlow()

    /** Records the premium; returns at once, the outcome arrives as [event]. */
    fun record(amount: Double, paidOn: LocalDate) {
        if (_recording.value) return
        _recording.value = true
        viewModelScope.launch {
            _event.value = when (val r = repo.markPaid(id, amount, paidOn)) {
                is Result.Ok -> PaymentEvent.Recorded(r.value)
                is Result.Err -> PaymentEvent.Failed(r.error)
            }
            _recording.value = false
        }
    }

    fun undo(recorded: PaymentEvent.Recorded) {
        viewModelScope.launch {
            when (val r = repo.undoPayment(recorded.result.paymentId)) {
                is Result.Ok -> Unit
                is Result.Err -> _event.value = if (r.error == AppError.Conflict) PaymentEvent.UndoRefused else PaymentEvent.Failed(r.error)
            }
        }
    }

    fun eventShown() { _event.value = null }

    companion object {
        const val AMOUNT = "amount"
        const val PAID_ON = "paid_on"

        /** The record-payment sheet's fields: amount (prefilled with the premium) and date (today). */
        val PAYMENT_FIELDS: List<FieldSpec> = listOf(
            FieldSpec(AMOUNT, FieldType.Money, Res.string.payment_amount, required = true, min = 1, max = 9_999_999_999L),
            FieldSpec(PAID_ON, FieldType.Date, Res.string.payment_date, required = true),
        )

        fun paymentForm(policy: Policy, today: LocalDate): FormState = FormState(
            PAYMENT_FIELDS,
            mapOf(AMOUNT to (policy.premiumAmount?.let(PolicyFormViewModel::amountText) ?: ""), PAID_ON to today),
        )

        /** "Next due becomes …": the same rule as `mark_premium_paid` (docs/BACKEND.md, Due date rule). */
        fun nextDueAfterPayment(policy: Policy): LocalDate? =
            policy.nextDueDate?.let { DueDateCalculator.nextAfterPayment(policy.startDate, it, policy.premiumMode) }

        fun parseNextDue(result: MarkPaidResult): LocalDate? = parseDateOrNull(result.nextDueDate)
    }
}
