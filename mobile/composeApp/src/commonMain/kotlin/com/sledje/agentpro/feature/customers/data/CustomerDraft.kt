package com.sledje.agentpro.feature.customers.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.data.parseInstantOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/** Where an unfinished customer form came from (`customer_drafts.source`; docs/BACKEND.md). */
enum class DraftSource(val key: String) {
    /** The agent's own unfinished customer form. */
    Agent("agent"),

    /** A sign-up link the customer fills in. */
    Customer("customer"),
    ;

    companion object {
        fun fromKey(key: String?): DraftSource = entries.firstOrNull { it.key == key } ?: Agent
    }
}

/** The keys of `customer_drafts.fields` (all strings; `notes` only on an agent draft). */
object DraftFields {
    const val NAME = "name"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val DOB = "dob"
    const val ADDRESS = "address"
    const val CITY = "city"
    const val NOMINEE = "nominee"
    const val NOTES = "notes"

    /** The fields a customer fills in, in form order (what the live screen shows). */
    val CUSTOMER_KEYS = listOf(NAME, PHONE, EMAIL, DOB, ADDRESS, CITY, NOMINEE)
}

/** An unfinished customer form (`customer_drafts`). A completed draft has become a customer ([customerId]). */
data class CustomerDraft(
    val id: String,
    val source: DraftSource,
    /** Partial values typed so far (strings, keys of [DraftFields]). */
    val fields: Map<String, String>,
    /** The field the customer is in now (customer links only). */
    val activeField: String?,
    /** The customer's last save or heartbeat. */
    val customerSeenAt: Instant?,
    val openedAt: Instant?,
    /** The customer ticked the privacy notice; no field is stored before this. */
    val consented: Boolean,
    val linkExpiresAt: Instant?,
    val completedAt: Instant?,
    val customerId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val name: String? get() = fields[DraftFields.NAME]?.trim()?.takeIf { it.isNotEmpty() }
    val isCompleted: Boolean get() = completedAt != null
}

@Serializable
data class CustomerDraftDto(
    val id: String,
    val source: String,
    val fields: JsonObject = JsonObject(emptyMap()),
    @SerialName("active_field") val activeField: String? = null,
    @SerialName("customer_seen_at") val customerSeenAt: String? = null,
    @SerialName("opened_at") val openedAt: String? = null,
    val consent: JsonElement? = null,
    @SerialName("link_expires_at") val linkExpiresAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "customer_drafts")
data class CustomerDraftEntity(
    @PrimaryKey val id: String,
    val source: String,
    /** The `fields` object as JSON text. */
    val fieldsJson: String,
    val activeField: String?,
    val customerSeenAt: String?,
    val openedAt: String?,
    val consented: Boolean,
    val linkExpiresAt: String?,
    val completedAt: String?,
    val customerId: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Dao
interface CustomerDraftDao {
    /** Live drafts that are not completed; the model list is sorted newest first by the repository. */
    @Query("SELECT * FROM customer_drafts WHERE deletedAt IS NULL AND completedAt IS NULL")
    fun observeIncomplete(): Flow<List<CustomerDraftEntity>>

    /** One live draft, completed or not (the live screen shows "Added as a customer"). */
    @Query("SELECT * FROM customer_drafts WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<CustomerDraftEntity?>

    @Upsert
    suspend fun upsert(rows: List<CustomerDraftEntity>)

    @Query("DELETE FROM customer_drafts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM customer_drafts")
    suspend fun deleteAll()
}

fun CustomerDraftDto.toEntity() = CustomerDraftEntity(
    id = id, source = source, fieldsJson = fields.toString(), activeField = activeField,
    customerSeenAt = customerSeenAt, openedAt = openedAt, consented = consent is JsonObject,
    linkExpiresAt = linkExpiresAt, completedAt = completedAt, customerId = customerId,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

fun CustomerDraftEntity.toModel() = CustomerDraft(
    id = id,
    source = DraftSource.fromKey(source),
    fields = DraftJson.fields(fieldsJson),
    activeField = activeField,
    customerSeenAt = parseInstantOrNull(customerSeenAt),
    openedAt = parseInstantOrNull(openedAt),
    consented = consented,
    linkExpiresAt = parseInstantOrNull(linkExpiresAt),
    completedAt = parseInstantOrNull(completedAt),
    customerId = customerId,
    createdAt = parseInstantOrNull(createdAt) ?: Instant.fromEpochMilliseconds(0),
    updatedAt = parseInstantOrNull(updatedAt) ?: Instant.fromEpochMilliseconds(0),
)

/** JSON <-> `fields` (pure, unit-tested). */
object DraftJson {
    /** Parses the stored `fields` text; anything that is not a string value is skipped. */
    fun fields(text: String): Map<String, String> {
        val o = runCatching { RowJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyMap()
        return o.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }.toMap()
    }

    /** The `fields` object sent to the server: blank values are left out. */
    fun toJson(fields: Map<String, String>): JsonObject =
        JsonObject(fields.filterValues { it.isNotBlank() }.mapValues { JsonPrimitive(it.value) })
}
