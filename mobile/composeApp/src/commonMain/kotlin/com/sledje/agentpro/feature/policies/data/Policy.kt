package com.sledje.agentpro.feature.policies.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.core.data.parseDateOrNull
import com.sledje.agentpro.core.data.parseInstantOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A policy (`policies`; docs/BACKEND.md). Its `next_due_date` moves only through `mark_premium_paid`/undo or an edit. */
data class Policy(
    val id: String,
    val customerId: String,
    val policyNumber: String?,
    val planName: String?,
    val sumAssured: Double?,
    val premiumAmount: Double?,
    val premiumMode: PremiumMode,
    val startDate: LocalDate?,
    val nextDueDate: LocalDate?,
    val status: PolicyStatus,
    val notes: String?,
) {
    /** Yearly premium (for "2 policies · ₹45,000/yr"). */
    val annualPremium: Double get() = (premiumAmount ?: 0.0) * (12.0 / premiumMode.months)
}

/** What the policy form saves (docs/UX.md, Policy form). */
data class PolicyInput(
    val customerId: String,
    val planName: String,
    val policyNumber: String,
    val premiumAmount: Double,
    val premiumMode: PremiumMode,
    val startDate: LocalDate,
    val nextDueDate: LocalDate,
    val status: PolicyStatus = PolicyStatus.Active,
    val sumAssured: Double? = null,
    val notes: String? = null,
)

/** One recorded premium (`premium_payments`; written only by the RPCs). */
data class PremiumPayment(
    val id: String,
    val policyId: String,
    val amount: Double,
    val paidOn: LocalDate,
    val periodDueDate: LocalDate,
    val createdAt: Instant,
)

/** `mark_premium_paid` → `{payment_id, period_due_date, next_due_date}`. */
@Serializable
data class MarkPaidResult(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("period_due_date") val periodDueDate: String,
    @SerialName("next_due_date") val nextDueDate: String,
)

@Serializable
data class PolicyDto(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("policy_number") val policyNumber: String? = null,
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("sum_assured") val sumAssured: Double? = null,
    @SerialName("premium_amount") val premiumAmount: Double? = null,
    @SerialName("premium_mode") val premiumMode: String,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    val status: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val policyNumber: String?,
    val planName: String?,
    val sumAssured: Double?,
    val premiumAmount: Double?,
    val premiumMode: String,
    val startDate: String?,
    val nextDueDate: String?,
    val status: String,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Serializable
data class PremiumPaymentDto(
    val id: String,
    @SerialName("policy_id") val policyId: String,
    val amount: Double,
    @SerialName("paid_on") val paidOn: String,
    @SerialName("period_due_date") val periodDueDate: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "premium_payments")
data class PremiumPaymentEntity(
    @PrimaryKey val id: String,
    val policyId: String,
    val amount: Double,
    val paidOn: String,
    val periodDueDate: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Dao
interface PolicyDao {
    /** Live policies; soft-deleted rows are filtered here (DECISIONS #27). */
    @Query("SELECT * FROM policies WHERE deletedAt IS NULL ORDER BY nextDueDate IS NULL, nextDueDate, createdAt")
    fun observeAll(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies WHERE customerId = :customerId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeForCustomer(customerId: String): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<PolicyEntity?>

    @Upsert
    suspend fun upsert(rows: List<PolicyEntity>)

    @Query("DELETE FROM policies WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM policies")
    suspend fun deleteAll()
}

@Dao
interface PremiumPaymentDao {
    /** A policy's live payments, latest period first (the order `undo_premium_payment` uses). */
    @Query("SELECT * FROM premium_payments WHERE policyId = :policyId AND deletedAt IS NULL ORDER BY periodDueDate DESC, createdAt DESC")
    fun observeForPolicy(policyId: String): Flow<List<PremiumPaymentEntity>>

    @Query("SELECT * FROM premium_payments WHERE deletedAt IS NULL ORDER BY paidOn DESC, createdAt DESC")
    fun observeAll(): Flow<List<PremiumPaymentEntity>>

    @Upsert
    suspend fun upsert(rows: List<PremiumPaymentEntity>)

    @Query("DELETE FROM premium_payments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM premium_payments")
    suspend fun deleteAll()
}

fun PolicyDto.toEntity() = PolicyEntity(
    id = id, customerId = customerId, policyNumber = policyNumber, planName = planName, sumAssured = sumAssured,
    premiumAmount = premiumAmount, premiumMode = premiumMode, startDate = startDate, nextDueDate = nextDueDate,
    status = status, notes = notes, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

fun PolicyEntity.toModel() = Policy(
    id = id, customerId = customerId, policyNumber = policyNumber, planName = planName, sumAssured = sumAssured,
    premiumAmount = premiumAmount,
    premiumMode = PremiumMode.fromKey(premiumMode) ?: PremiumMode.Yearly,
    startDate = parseDateOrNull(startDate), nextDueDate = parseDateOrNull(nextDueDate),
    status = PolicyStatus.fromKey(status) ?: PolicyStatus.Active,
    notes = notes,
)

fun PremiumPaymentDto.toEntity() = PremiumPaymentEntity(
    id = id, policyId = policyId, amount = amount, paidOn = paidOn, periodDueDate = periodDueDate,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

fun PremiumPaymentEntity.toModel(): PremiumPayment? {
    val paid = parseDateOrNull(paidOn) ?: return null
    val period = parseDateOrNull(periodDueDate) ?: return null
    return PremiumPayment(id, policyId, amount, paid, period, parseInstantOrNull(createdAt) ?: Instant.fromEpochMilliseconds(0))
}
