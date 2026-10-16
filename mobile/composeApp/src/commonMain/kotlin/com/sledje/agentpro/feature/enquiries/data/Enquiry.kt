package com.sledje.agentpro.feature.enquiries.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.data.parseInstantOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A lead (`enquiries`; docs/BACKEND.md). From the website form (`source = website`) or added by hand. */
data class Enquiry(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val interest: Interest?,
    val source: EnquirySource?,
    val message: String?,
    val status: EnquiryStatus,
    val followUpAt: Instant?,
    val notes: String?,
    val seenAt: Instant?,
    val customerId: String?,
    val createdAt: Instant,
) {
    /** The small blue dot in the list (docs/UX.md, Enquiries — list). */
    val unseen: Boolean get() = seenAt == null
}

/** What "Add enquiry" saves (docs/UX.md: name, phone, interest; source and message under More details). */
data class NewEnquiry(
    val name: String,
    val phone: String?,
    val interest: String?,
    val source: String,
    val message: String?,
)

/** An `enquiries` row as PostgREST returns it. */
@Serializable
data class EnquiryDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val interest: String? = null,
    val source: String,
    val message: String? = null,
    val status: String,
    @SerialName("follow_up_at") val followUpAt: String? = null,
    val notes: String? = null,
    @SerialName("seen_at") val seenAt: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "enquiries")
data class EnquiryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val interest: String?,
    val source: String,
    val message: String?,
    val status: String,
    val followUpAt: String?,
    val notes: String?,
    val seenAt: String?,
    val customerId: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Dao
interface EnquiryDao {
    /** Live enquiries, newest first; soft-deleted rows are filtered here (DECISIONS #27). */
    @Query("SELECT * FROM enquiries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EnquiryEntity>>

    @Query("SELECT * FROM enquiries WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<EnquiryEntity?>

    @Upsert
    suspend fun upsert(rows: List<EnquiryEntity>)

    @Query("DELETE FROM enquiries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM enquiries")
    suspend fun deleteAll()
}

fun EnquiryDto.toEntity() = EnquiryEntity(
    id = id, name = name, phone = phone, email = email, interest = interest, source = source, message = message,
    status = status, followUpAt = followUpAt, notes = notes, seenAt = seenAt, customerId = customerId,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

fun EnquiryEntity.toModel() = Enquiry(
    id = id, name = name, phone = phone, email = email,
    interest = interest?.let { Interest.fromKey(it) },
    source = EnquirySource.fromKey(source),
    message = message,
    // An unknown (newer) status shows as New rather than crashing; the server CHECK keeps keys valid.
    status = EnquiryStatus.fromKey(status) ?: EnquiryStatus.New,
    followUpAt = parseInstantOrNull(followUpAt),
    notes = notes,
    seenAt = parseInstantOrNull(seenAt),
    customerId = customerId,
    createdAt = parseInstantOrNull(createdAt) ?: Instant.fromEpochMilliseconds(0),
)
