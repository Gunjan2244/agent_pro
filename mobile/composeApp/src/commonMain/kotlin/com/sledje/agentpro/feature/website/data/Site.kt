package com.sledje.agentpro.feature.website.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.data.RowJson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** The agent's website (`sites`, one per agent; docs/BACKEND.md). */
data class Site(
    val id: String,
    val slug: String,
    val templateKey: String,
    val accent: AccentSwatch?,
    val locale: String?,
    val draftContent: JsonObject,
    val isPublished: Boolean,
    val publishedAt: Instant?,
    /** The live content (`published_content`), null until the first publish. */
    val publishedContent: JsonObject? = null,
) {
    /** The draft differs from what's live (or nothing is live yet): the hub shows "Unpublished changes". */
    val hasUnpublishedChanges: Boolean get() = !isPublished || publishedContent != draftContent
}

/** What onboarding step 3 inserts. */
data class NewSite(
    val slug: String,
    val templateKey: String,
    val accent: String?,
    val draftContent: JsonObject,
)

/** One `publish_site()` validation error: `{section, field, item, rule}` (docs/BACKEND.md, Publish validation). */
@Serializable
data class PublishError(
    val section: String? = null,
    val field: String? = null,
    val item: Int? = null,
    val rule: String? = null,
)

sealed interface PublishOutcome {
    data class Published(val at: Instant?) : PublishOutcome
    data class Invalid(val errors: List<PublishError>) : PublishOutcome
}

/** A `sites` row as PostgREST returns it. */
@Serializable
data class SiteDto(
    val id: String,
    val slug: String,
    @SerialName("template_key") val templateKey: String,
    val accent: String? = null,
    val locale: String? = null,
    @SerialName("draft_content") val draftContent: JsonObject = JsonObject(emptyMap()),
    @SerialName("published_content") val publishedContent: JsonObject? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val templateKey: String,
    val accent: String?,
    val locale: String?,
    /** JSON text of `draft_content`. */
    val draftContent: String,
    /** JSON text of `published_content`. */
    val publishedContent: String?,
    val isPublished: Boolean,
    val publishedAt: String?,
    val updatedAt: String,
    val deletedAt: String?,
)

@Dao
interface SiteDao {
    /** The agent's live site; soft-deleted rows are filtered here (DECISIONS #27). */
    @Query("SELECT * FROM sites WHERE deletedAt IS NULL LIMIT 1")
    fun observeMine(): Flow<SiteEntity?>

    @Upsert
    suspend fun upsert(rows: List<SiteEntity>)

    @Query("DELETE FROM sites WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM sites")
    suspend fun deleteAll()
}

fun SiteDto.toEntity() = SiteEntity(
    id = id, slug = slug, templateKey = templateKey, accent = accent, locale = locale,
    draftContent = RowJson.encodeToString(JsonObject.serializer(), draftContent),
    publishedContent = publishedContent?.let { RowJson.encodeToString(JsonObject.serializer(), it) },
    isPublished = isPublished, publishedAt = publishedAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

fun SiteEntity.toModel() = Site(
    id = id, slug = slug, templateKey = templateKey,
    accent = accent?.let { AccentSwatch.fromKey(it) }, locale = locale,
    draftContent = runCatching { RowJson.decodeFromString(JsonObject.serializer(), draftContent) }.getOrDefault(JsonObject(emptyMap())),
    isPublished = isPublished,
    publishedAt = publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    publishedContent = publishedContent?.let { runCatching { RowJson.decodeFromString(JsonObject.serializer(), it) }.getOrNull() },
)
