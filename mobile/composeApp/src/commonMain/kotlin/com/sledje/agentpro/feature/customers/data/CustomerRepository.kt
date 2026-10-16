package com.sledje.agentpro.feature.customers.data

import com.sledje.agentpro.core.catalog.EnquiryStatus
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
import com.sledje.agentpro.core.data.updateRowsWhere
import com.sledje.agentpro.feature.enquiries.data.ENQUIRIES_TABLE
import com.sledje.agentpro.feature.policies.data.POLICIES_TABLE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

const val CUSTOMERS_TABLE = "customers"

interface CustomerRepository {
    fun observeAll(): Flow<List<Customer>>

    fun observe(id: String): Flow<Customer?>

    suspend fun create(input: CustomerInput): AppResult<Customer>

    suspend fun update(id: String, input: CustomerInput): AppResult<Customer>

    /**
     * Converts an enquiry (docs/UX.md, Enquiry detail → Convert to customer): inserts the customer with
     * `source_enquiry_id`, then sets the enquiry's `customer_id` and status `converted`. If a customer
     * from this enquiry already exists (a retry after the second step failed), it is reused, so a
     * retry never creates a duplicate.
     */
    suspend fun convertEnquiry(enquiryId: String, input: CustomerInput): AppResult<Customer>

    /**
     * Soft-deletes the customer and its policies (sets `deleted_at`; DECISIONS #27). Confirmed by a
     * dialog, not Undo (docs/UX.md, Global rule 2).
     */
    suspend fun delete(id: String): AppResult<Unit>

    suspend fun refresh(): AppResult<Unit>
}

fun customersSyncTable(dao: CustomerDao): SyncTable = RoomSyncTable(
    name = CUSTOMERS_TABLE,
    serializer = CustomerDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

/** The `customers` columns the form writes (pure, unit-tested). */
fun CustomerInput.toRow(): JsonObject = buildJsonObject {
    put("name", JsonPrimitive(name.trim()))
    put("phone", phone.toJson())
    put("email", email?.lowercase().toJson())
    put("dob", dob?.toString().toJson())
    put("address", address.toJson())
    put("city", city.toJson())
    put("nominee", nominee.toJson())
    put("notes", notes.toJson())
}

class SupabaseCustomerRepository(
    private val supabase: SupabaseProvider,
    private val dao: CustomerDao,
    private val sync: SyncEngine,
    private val clock: Clock = Clock.System,
) : CustomerRepository {
    override fun observeAll(): Flow<List<Customer>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observe(id: String): Flow<Customer?> = dao.observe(id).map { it?.toModel() }

    override suspend fun create(input: CustomerInput): AppResult<Customer> = write { supabase.insertRow(CUSTOMERS_TABLE, input.toRow()) }

    override suspend fun update(id: String, input: CustomerInput): AppResult<Customer> =
        write { supabase.updateRow(CUSTOMERS_TABLE, id, input.toRow()) }

    override suspend fun convertEnquiry(enquiryId: String, input: CustomerInput): AppResult<Customer> {
        val existing = dao.observeAll().first().firstOrNull { it.sourceEnquiryId == enquiryId }?.toModel()
        val customer = existing ?: when (
            val created = write {
                supabase.insertRow(
                    CUSTOMERS_TABLE,
                    buildJsonObject {
                        input.toRow().forEach { (k, v) -> put(k, v) }
                        put("source_enquiry_id", JsonPrimitive(enquiryId))
                    },
                )
            }
        ) {
            is Result.Ok -> created.value
            is Result.Err -> return created
        }
        val linked = remoteCall {
            supabase.updateRow(
                ENQUIRIES_TABLE,
                enquiryId,
                buildJsonObject {
                    put("customer_id", JsonPrimitive(customer.id))
                    put("status", JsonPrimitive(EnquiryStatus.Converted.key))
                },
            )
        }
        if (linked is Result.Err) return linked
        sync.applyLocal(ENQUIRIES_TABLE, listOf((linked as Result.Ok).value))
        return Result.Ok(customer)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        val now = JsonPrimitive(clock.now().toString())
        val patch = buildJsonObject { put("deleted_at", now) }
        val policies = remoteCall { supabase.updateRowsWhere(POLICIES_TABLE, "customer_id", id, patch) }
        if (policies is Result.Err) return policies
        sync.applyLocal(POLICIES_TABLE, (policies as Result.Ok).value)
        return write { supabase.updateRow(CUSTOMERS_TABLE, id, patch) }.map { }
    }

    override suspend fun refresh(): AppResult<Unit> = sync.refresh(CUSTOMERS_TABLE).map { }

    private suspend fun write(block: suspend () -> JsonObject): AppResult<Customer> {
        val result = remoteCall { block() }
        if (result is Result.Err) return result
        val row = (result as Result.Ok).value
        sync.applyLocal(CUSTOMERS_TABLE, listOf(row))
        return Result.Ok(row.decodeRow(CustomerDto.serializer()).toEntity().toModel())
    }
}
