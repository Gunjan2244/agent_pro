package com.sledje.agentpro.feature.settings.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The signed-in agent's profile (`profiles`, docs/BACKEND.md). The row is created by the signup trigger. */
data class Profile(
    val id: String,
    val fullName: String?,
    val phone: String?,
    val email: String?,
    val designation: String?,
    val agencyCode: String?,
    val branch: String?,
    val city: String?,
    val photoUrl: String?,
    val preferredLanguage: String?,
) {
    /** Onboarding step 1 is done once the required fields (name and mobile) are filled. */
    val isComplete: Boolean get() = !fullName.isNullOrBlank() && !phone.isNullOrBlank()

    /** "Namaste, {first name}" on Home. */
    val firstName: String? get() = fullName?.trim()?.split(' ')?.firstOrNull { it.isNotBlank() }
}

/** What onboarding step 1 (and later Settings → Profile) writes. Blank optional values are stored as null. */
data class ProfileInput(
    val fullName: String,
    val phone: String,
    val designation: String? = null,
    val city: String? = null,
    val agencyCode: String? = null,
    val branch: String? = null,
    /** Public URL of the uploaded photo (`site-media/<uid>/…`); null removes it. */
    val photoUrl: String? = null,
)

/** A `profiles` row as PostgREST returns it. */
@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val designation: String? = null,
    @SerialName("agency_code") val agencyCode: String? = null,
    val branch: String? = null,
    val city: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val fullName: String?,
    val phone: String?,
    val email: String?,
    val designation: String?,
    val agencyCode: String?,
    val branch: String?,
    val city: String?,
    val photoUrl: String?,
    val preferredLanguage: String?,
    val updatedAt: String,
)

@Dao
interface ProfileDao {
    /** The cache only ever holds the signed-in agent's own profile (it's cleared on sign-out). */
    @Query("SELECT * FROM profiles LIMIT 1")
    fun observeMine(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(rows: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

fun ProfileDto.toEntity() = ProfileEntity(
    id = id, fullName = fullName, phone = phone, email = email, designation = designation,
    agencyCode = agencyCode, branch = branch, city = city, photoUrl = photoUrl,
    preferredLanguage = preferredLanguage, updatedAt = updatedAt,
)

fun ProfileEntity.toModel() = Profile(
    id = id, fullName = fullName, phone = phone, email = email, designation = designation,
    agencyCode = agencyCode, branch = branch, city = city, photoUrl = photoUrl,
    preferredLanguage = preferredLanguage,
)
