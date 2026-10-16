package com.sledje.agentpro.feature.customers.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.sledje.agentpro.core.data.parseDateOrNull
import com.sledje.agentpro.core.data.parseInstantOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A customer (`customers`; docs/BACKEND.md). [sourceEnquiryId] is set when converted from an enquiry. */
data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val dob: LocalDate?,
    val address: String?,
    val city: String?,
    val nominee: String?,
    val notes: String?,
    val sourceEnquiryId: String?,
    val createdAt: Instant,
)

/** What the customer form saves (docs/UX.md, Customer form). */
data class CustomerInput(
    val name: String,
    val phone: String?,
    val email: String? = null,
    val dob: LocalDate? = null,
    val address: String? = null,
    val city: String? = null,
    val nominee: String? = null,
    val notes: String? = null,
)

@Serializable
data class CustomerDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val dob: String? = null,
    val address: String? = null,
    val city: String? = null,
    val nominee: String? = null,
    val notes: String? = null,
    @SerialName("source_enquiry_id") val sourceEnquiryId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val dob: String?,
    val address: String?,
    val city: String?,
    val nominee: String?,
    val notes: String?,
    val sourceEnquiryId: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Dao
interface CustomerDao {
    /** Live customers by name; soft-deleted rows are filtered here (DECISIONS #27). */
    @Query("SELECT * FROM customers WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<CustomerEntity?>

    @Upsert
    suspend fun upsert(rows: List<CustomerEntity>)

    @Query("DELETE FROM customers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

fun CustomerDto.toEntity() = CustomerEntity(
    id = id, name = name, phone = phone, email = email, dob = dob, address = address, city = city,
    nominee = nominee, notes = notes, sourceEnquiryId = sourceEnquiryId, createdAt = createdAt,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

fun CustomerEntity.toModel() = Customer(
    id = id, name = name, phone = phone, email = email, dob = parseDateOrNull(dob), address = address, city = city,
    nominee = nominee, notes = notes, sourceEnquiryId = sourceEnquiryId,
    createdAt = parseInstantOrNull(createdAt) ?: Instant.fromEpochMilliseconds(0),
)
