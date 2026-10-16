package com.sledje.agentpro.testing

import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.domain.DueDateCalculator
import com.sledje.agentpro.feature.customers.data.Customer
import com.sledje.agentpro.feature.customers.data.CustomerInput
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.feature.enquiries.data.EnquiryLive
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import com.sledje.agentpro.feature.enquiries.data.NewEnquiry
import com.sledje.agentpro.feature.policies.data.MarkPaidResult
import com.sledje.agentpro.feature.policies.data.Policy
import com.sledje.agentpro.feature.policies.data.PolicyInput
import com.sledje.agentpro.feature.policies.data.PolicyRepository
import com.sledje.agentpro.feature.policies.data.PremiumPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/*
 * Fakes of the Phase 6–7 repositories (enquiries, customers, policies) and the realtime seam. They keep
 * rows in memory, behave like the Supabase implementations (status `converted` on convert, latest-only
 * Undo, the due-date rule of mark_premium_paid) and never call a backend.
 */

val TEST_NOW: Instant = Instant.parse("2026-10-16T04:30:00Z")
val TEST_TODAY: LocalDate = LocalDate(2026, 10, 16)

fun testEnquiry(
    id: String = "enq-1",
    name: String = "Sunita Sharma",
    status: EnquiryStatus = EnquiryStatus.New,
    interest: Interest? = Interest.Term,
    source: EnquirySource = EnquirySource.Website,
    seen: Boolean = false,
    createdAt: Instant = TEST_NOW,
    customerId: String? = null,
    followUpAt: Instant? = null,
) = Enquiry(
    id = id, name = name, phone = "+919812345678", email = "sunita@example.com", interest = interest, source = source,
    message = "Please call me about a term plan", status = status, followUpAt = followUpAt, notes = null,
    seenAt = if (seen) createdAt else null, customerId = customerId, createdAt = createdAt,
)

fun testCustomer(id: String = "cus-1", name: String = "Sunita Sharma", sourceEnquiryId: String? = null) = Customer(
    id = id, name = name, phone = "+919812345678", email = "sunita@example.com", dob = null, address = null,
    city = "Pune", nominee = null, notes = null, sourceEnquiryId = sourceEnquiryId, createdAt = TEST_NOW,
)

fun testPolicy(
    id: String = "pol-1",
    customerId: String = "cus-1",
    planName: String = "Jeevan Anand",
    premium: Double = 12_000.0,
    mode: PremiumMode = PremiumMode.Quarterly,
    start: LocalDate = LocalDate(2026, 1, 15),
    nextDue: LocalDate? = LocalDate(2026, 10, 15),
    status: PolicyStatus = PolicyStatus.Active,
) = Policy(
    id = id, customerId = customerId, policyNumber = "123456789", planName = planName, sumAssured = 500_000.0,
    premiumAmount = premium, premiumMode = mode, startDate = start, nextDueDate = nextDue, status = status, notes = null,
)

class FakeEnquiryRepository(initial: List<Enquiry> = emptyList()) : EnquiryRepository {
    val rows = MutableStateFlow(initial)
    var error: AppError? = null
    val calls = mutableListOf<String>()
    private var nextId = 100

    private fun change(id: String, transform: (Enquiry) -> Enquiry): AppResult<Enquiry> {
        error?.let { return Result.Err(it) }
        val current = rows.value.firstOrNull { it.id == id } ?: return Result.Err(AppError.Unknown("not found"))
        val updated = transform(current)
        rows.value = rows.value.map { if (it.id == id) updated else it }
        return Result.Ok(updated)
    }

    override fun observeAll(): Flow<List<Enquiry>> = rows.map { list -> list.sortedByDescending { it.createdAt } }
    override fun observe(id: String): Flow<Enquiry?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun add(input: NewEnquiry): AppResult<Enquiry> {
        calls += "add:${input.name}"
        error?.let { return Result.Err(it) }
        val e = Enquiry(
            id = "enq-${nextId++}", name = input.name, phone = input.phone, email = null,
            interest = input.interest?.let { Interest.fromKey(it) }, source = EnquirySource.fromKey(input.source),
            message = input.message, status = EnquiryStatus.New, followUpAt = null, notes = null, seenAt = TEST_NOW,
            customerId = null, createdAt = TEST_NOW,
        )
        rows.value = rows.value + e
        return Result.Ok(e)
    }

    override suspend fun setStatus(id: String, status: EnquiryStatus, followUpAt: Instant?): AppResult<Enquiry> {
        calls += "status:$id:${status.key}"
        return change(id) { it.copy(status = status, followUpAt = if (status == EnquiryStatus.FollowUp && followUpAt != null) followUpAt else it.followUpAt) }
    }

    override suspend fun setFollowUp(id: String, at: Instant?): AppResult<Enquiry> { calls += "followUp:$id"; return change(id) { it.copy(followUpAt = at) } }
    override suspend fun setNotes(id: String, notes: String?): AppResult<Enquiry> { calls += "notes:$id"; return change(id) { it.copy(notes = notes) } }

    override suspend fun markSeen(id: String): AppResult<Unit> {
        calls += "seen:$id"
        return when (val r = change(id) { it.copy(seenAt = TEST_NOW) }) { is Result.Ok -> Result.Ok(Unit); is Result.Err -> r }
    }

    private val deleted = mutableMapOf<String, Enquiry>()

    override suspend fun delete(id: String): AppResult<Unit> {
        calls += "delete:$id"
        error?.let { return Result.Err(it) }
        rows.value.firstOrNull { it.id == id }?.let { deleted[id] = it }
        rows.value = rows.value.filterNot { it.id == id }
        return Result.Ok(Unit)
    }

    override suspend fun restore(id: String): AppResult<Enquiry> {
        calls += "restore:$id"
        val e = deleted.remove(id) ?: return Result.Err(AppError.Unknown("not deleted"))
        rows.value = rows.value + e
        return Result.Ok(e)
    }

    var refreshes = 0
    override suspend fun refresh(): AppResult<Unit> { refreshes++; return error?.let { Result.Err(it) } ?: Result.Ok(Unit) }
}

class FakeCustomerRepository(
    initial: List<Customer> = emptyList(),
    /** The enquiries fake that [convertEnquiry] updates, as the real repository updates the row. */
    private val enquiries: FakeEnquiryRepository? = null,
    private val policies: FakePolicyRepository? = null,
) : CustomerRepository {
    val rows = MutableStateFlow(initial)
    var error: AppError? = null
    /** Fails only the second step of convert (linking the enquiry). */
    var linkError: AppError? = null
    val calls = mutableListOf<String>()
    private var nextId = 100

    override fun observeAll(): Flow<List<Customer>> = rows.map { list -> list.sortedBy { it.name.lowercase() } }
    override fun observe(id: String): Flow<Customer?> = rows.map { list -> list.firstOrNull { it.id == id } }

    private fun newCustomer(input: CustomerInput, source: String?) = Customer(
        id = "cus-${nextId++}", name = input.name, phone = input.phone, email = input.email, dob = input.dob,
        address = input.address, city = input.city, nominee = input.nominee, notes = input.notes,
        sourceEnquiryId = source, createdAt = TEST_NOW,
    )

    override suspend fun create(input: CustomerInput): AppResult<Customer> {
        calls += "create:${input.name}"
        error?.let { return Result.Err(it) }
        val c = newCustomer(input, null)
        rows.value = rows.value + c
        return Result.Ok(c)
    }

    override suspend fun update(id: String, input: CustomerInput): AppResult<Customer> {
        calls += "update:$id"
        error?.let { return Result.Err(it) }
        val current = rows.value.firstOrNull { it.id == id } ?: return Result.Err(AppError.Unknown("not found"))
        val updated = current.copy(
            name = input.name, phone = input.phone, email = input.email, dob = input.dob, address = input.address,
            city = input.city, nominee = input.nominee, notes = input.notes,
        )
        rows.value = rows.value.map { if (it.id == id) updated else it }
        return Result.Ok(updated)
    }

    override suspend fun convertEnquiry(enquiryId: String, input: CustomerInput): AppResult<Customer> {
        calls += "convert:$enquiryId"
        error?.let { return Result.Err(it) }
        val customer = rows.value.firstOrNull { it.sourceEnquiryId == enquiryId }
            ?: newCustomer(input, enquiryId).also { rows.value = rows.value + it }
        linkError?.let { return Result.Err(it) }
        enquiries?.rows?.value = enquiries?.rows?.value.orEmpty().map {
            if (it.id == enquiryId) it.copy(customerId = customer.id, status = EnquiryStatus.Converted) else it
        }
        return Result.Ok(customer)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        calls += "delete:$id"
        error?.let { return Result.Err(it) }
        rows.value = rows.value.filterNot { it.id == id }
        policies?.rows?.value = policies?.rows?.value.orEmpty().filterNot { it.customerId == id }
        return Result.Ok(Unit)
    }

    override suspend fun refresh(): AppResult<Unit> = error?.let { Result.Err(it) } ?: Result.Ok(Unit)
}

class FakePolicyRepository(initial: List<Policy> = emptyList(), payments: List<PremiumPayment> = emptyList()) : PolicyRepository {
    val rows = MutableStateFlow(initial)
    val paymentRows = MutableStateFlow(payments)
    var error: AppError? = null
    var undoError: AppError? = null
    val calls = mutableListOf<String>()
    private var nextId = 100

    override fun observeAll(): Flow<List<Policy>> = rows
    override fun observeForCustomer(customerId: String): Flow<List<Policy>> = rows.map { list -> list.filter { it.customerId == customerId } }
    override fun observe(id: String): Flow<Policy?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override fun observePayments(policyId: String): Flow<List<PremiumPayment>> =
        paymentRows.map { list -> list.filter { it.policyId == policyId }.sortedByDescending { it.periodDueDate } }

    override fun observeAllPayments(): Flow<List<PremiumPayment>> = paymentRows

    private fun PolicyInput.toPolicy(id: String) = Policy(
        id = id, customerId = customerId, policyNumber = policyNumber, planName = planName, sumAssured = sumAssured,
        premiumAmount = premiumAmount, premiumMode = premiumMode, startDate = startDate, nextDueDate = nextDueDate,
        status = status, notes = notes,
    )

    override suspend fun create(input: PolicyInput): AppResult<Policy> {
        calls += "create:${input.planName}"
        error?.let { return Result.Err(it) }
        val p = input.toPolicy("pol-${nextId++}")
        rows.value = rows.value + p
        return Result.Ok(p)
    }

    override suspend fun update(id: String, input: PolicyInput): AppResult<Policy> {
        calls += "update:$id"
        error?.let { return Result.Err(it) }
        val p = input.toPolicy(id)
        rows.value = rows.value.map { if (it.id == id) p else it }
        return Result.Ok(p)
    }

    override suspend fun markPaid(policyId: String, amount: Double, paidOn: LocalDate): AppResult<MarkPaidResult> {
        calls += "markPaid:$policyId:$amount:$paidOn"
        error?.let { return Result.Err(it) }
        val policy = rows.value.firstOrNull { it.id == policyId } ?: return Result.Err(AppError.Unknown("not found"))
        val due = policy.nextDueDate ?: return Result.Err(AppError.Validation("value"))
        val next = DueDateCalculator.nextAfterPayment(policy.startDate, due, policy.premiumMode)
        val payment = PremiumPayment("pay-${nextId++}", policyId, amount, paidOn, due, TEST_NOW)
        paymentRows.value = paymentRows.value + payment
        rows.value = rows.value.map { if (it.id == policyId) it.copy(nextDueDate = next) else it }
        return Result.Ok(MarkPaidResult(payment.id, due.toString(), next.toString()))
    }

    override suspend fun undoPayment(paymentId: String): AppResult<Unit> {
        calls += "undo:$paymentId"
        undoError?.let { return Result.Err(it) }
        val payment = paymentRows.value.firstOrNull { it.id == paymentId } ?: return Result.Err(AppError.Unknown("not found"))
        val latest = paymentRows.value.filter { it.policyId == payment.policyId }.maxByOrNull { it.periodDueDate }
        if (latest?.id != paymentId) return Result.Err(AppError.Conflict)
        paymentRows.value = paymentRows.value.filterNot { it.id == paymentId }
        rows.value = rows.value.map { if (it.id == payment.policyId) it.copy(nextDueDate = payment.periodDueDate) else it }
        return Result.Ok(Unit)
    }

    override suspend fun refresh(): AppResult<Unit> = error?.let { Result.Err(it) } ?: Result.Ok(Unit)
}

class FakeEnquiryLive : EnquiryLive {
    var started = 0
    var stopped = 0
    override fun start() { started++ }
    override fun stop() { stopped++ }
}

/** Returns [contact] when opened (null = the agent cancelled). */
class FakeContactPicker(var contact: com.sledje.agentpro.core.platform.PickedContact? = null) : com.sledje.agentpro.core.platform.ContactPicker {
    var opened = 0

    @androidx.compose.runtime.Composable
    override fun rememberContactPicker(onResult: (com.sledje.agentpro.core.platform.PickedContact?) -> Unit): () -> Unit =
        { opened++; onResult(contact) }
}
