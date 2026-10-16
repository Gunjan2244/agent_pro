package com.sledje.agentpro.feature.policies.data

import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.RoomSyncTable
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.data.SyncTable
import com.sledje.agentpro.core.data.decodeRow
import com.sledje.agentpro.core.data.insertRow
import com.sledje.agentpro.core.data.map
import com.sledje.agentpro.core.data.remoteCall
import com.sledje.agentpro.core.data.toJson
import com.sledje.agentpro.core.data.updateRow
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

const val POLICIES_TABLE = "policies"
const val PREMIUM_PAYMENTS_TABLE = "premium_payments"

/**
 * Policies and premium payments. Policies are written directly (RLS); payments only through the RPCs
 * `mark_premium_paid` / `undo_premium_payment` (docs/BACKEND.md, Client write rules), after which both
 * tables are refreshed so Room has the new `next_due_date` and payment row.
 */
interface PolicyRepository {
    fun observeAll(): Flow<List<Policy>>

    fun observeForCustomer(customerId: String): Flow<List<Policy>>

    fun observe(id: String): Flow<Policy?>

    /** A policy's live payments, latest period first. */
    fun observePayments(policyId: String): Flow<List<PremiumPayment>>

    fun observeAllPayments(): Flow<List<PremiumPayment>>

    suspend fun create(input: PolicyInput): AppResult<Policy>

    suspend fun update(id: String, input: PolicyInput): AppResult<Policy>

    /** `mark_premium_paid(policy_id, amount, paid_on)`; the server moves `next_due_date` forward. */
    suspend fun markPaid(policyId: String, amount: Double, paidOn: LocalDate): AppResult<MarkPaidResult>

    /** `undo_premium_payment(payment_id)`: only the latest live payment (else `Conflict`; DECISIONS #31). */
    suspend fun undoPayment(paymentId: String): AppResult<Unit>

    suspend fun refresh(): AppResult<Unit>
}

fun policiesSyncTable(dao: PolicyDao): SyncTable = RoomSyncTable(
    name = POLICIES_TABLE,
    serializer = PolicyDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

fun premiumPaymentsSyncTable(dao: PremiumPaymentDao): SyncTable = RoomSyncTable(
    name = PREMIUM_PAYMENTS_TABLE,
    serializer = PremiumPaymentDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

/** The `policies` columns the form writes (pure, unit-tested). */
fun PolicyInput.toRow(): JsonObject = buildJsonObject {
    put("customer_id", JsonPrimitive(customerId))
    put("plan_name", JsonPrimitive(planName.trim()))
    put("policy_number", JsonPrimitive(policyNumber.trim()))
    put("premium_amount", JsonPrimitive(premiumAmount))
    put("premium_mode", JsonPrimitive(premiumMode.key))
    put("start_date", JsonPrimitive(startDate.toString()))
    put("next_due_date", JsonPrimitive(nextDueDate.toString()))
    put("status", JsonPrimitive(status.key))
    put("sum_assured", sumAssured.toJson())
    put("notes", notes.toJson())
}

class SupabasePolicyRepository(
    private val supabase: SupabaseProvider,
    private val policies: PolicyDao,
    private val payments: PremiumPaymentDao,
    private val sync: SyncEngine,
) : PolicyRepository {
    override fun observeAll(): Flow<List<Policy>> = policies.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeForCustomer(customerId: String): Flow<List<Policy>> =
        policies.observeForCustomer(customerId).map { rows -> rows.map { it.toModel() } }

    override fun observe(id: String): Flow<Policy?> = policies.observe(id).map { it?.toModel() }

    override fun observePayments(policyId: String): Flow<List<PremiumPayment>> =
        payments.observeForPolicy(policyId).map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observeAllPayments(): Flow<List<PremiumPayment>> = payments.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun create(input: PolicyInput): AppResult<Policy> = write { supabase.insertRow(POLICIES_TABLE, input.toRow()) }

    override suspend fun update(id: String, input: PolicyInput): AppResult<Policy> =
        write { supabase.updateRow(POLICIES_TABLE, id, input.toRow()) }

    override suspend fun markPaid(policyId: String, amount: Double, paidOn: LocalDate): AppResult<MarkPaidResult> {
        val result = remoteCall {
            supabase.require().postgrest.rpc(
                "mark_premium_paid",
                buildJsonObject {
                    put("policy_id", JsonPrimitive(policyId))
                    put("amount", JsonPrimitive(amount))
                    put("paid_on", JsonPrimitive(paidOn.toString()))
                },
            ).decodeAs<JsonObject>().decodeRow(MarkPaidResult.serializer())
        }
        if (result is Result.Ok) refreshBoth()
        return result
    }

    override suspend fun undoPayment(paymentId: String): AppResult<Unit> {
        val result = remoteCall {
            supabase.require().postgrest.rpc("undo_premium_payment", buildJsonObject { put("payment_id", JsonPrimitive(paymentId)) })
            Unit
        }
        if (result is Result.Ok) refreshBoth()
        return result
    }

    override suspend fun refresh(): AppResult<Unit> {
        val first = sync.refresh(POLICIES_TABLE)
        val second = sync.refresh(PREMIUM_PAYMENTS_TABLE)
        return if (first is Result.Err) first.map { } else second.map { }
    }

    private suspend fun refreshBoth() {
        sync.refresh(POLICIES_TABLE)
        sync.refresh(PREMIUM_PAYMENTS_TABLE)
    }

    private suspend fun write(block: suspend () -> JsonObject): AppResult<Policy> {
        val result = remoteCall { block() }
        if (result is Result.Err) return result
        val row = (result as Result.Ok).value
        sync.applyLocal(POLICIES_TABLE, listOf(row))
        return Result.Ok(row.decodeRow(PolicyDto.serializer()).toEntity().toModel())
    }
}
