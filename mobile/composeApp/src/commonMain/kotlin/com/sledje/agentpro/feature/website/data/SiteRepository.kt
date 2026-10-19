package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.app.AppConfig
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.RoomSyncTable
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.data.SyncTable
import com.sledje.agentpro.core.data.remoteCall
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** The agent's site: read from Room; created, checked and published through Supabase. */
interface SiteRepository {
    fun observeMine(): Flow<Site?>

    /**
     * Asks the server, not the cache, whether the agent already has a site, and caches it if so.
     * Checked before onboarding offers to create one, so an agent never makes a second site or a new link.
     */
    suspend fun fetchMine(): AppResult<Site?>

    /** `is_slug_available(slug)`: format, reserved words and other agents' sites (docs/BACKEND.md, RPCs). */
    suspend fun isSlugAvailable(slug: String): AppResult<Boolean>

    /** Inserts the agent's one site. A taken slug comes back as [AppError.Conflict]. */
    suspend fun create(site: NewSite): AppResult<Site>

    /** `publish_site()`: validates the draft on the server and copies it to published. */
    suspend fun publish(): AppResult<PublishOutcome>

    /**
     * Writes `draft_content` only (docs/UX.md, Section editor: "Saving writes only the draft"). Content over
     * the 64 KB cap is refused locally as `Validation("content", "too_large")`.
     */
    suspend fun saveDraft(content: JsonObject): AppResult<Site>

    /** Template, accent and site language. These columns are read by the live site directly (no publish needed). */
    suspend fun updateAppearance(appearance: SiteAppearance): AppResult<Site>

    /** Changes the free link. A slug taken meanwhile comes back as [AppError.Conflict]. */
    suspend fun changeSlug(slug: String): AppResult<Site>
}

/** `sites.template_key`, `accent` (swatch key) and `locale` (null = the site follows the default language). */
data class SiteAppearance(val templateKey: String, val accent: String?, val locale: String?)

/** Why [SiteRepository.saveDraft] refused content larger than [SiteContent.MAX_BYTES]. */
val ContentTooLarge = AppError.Validation("content", "too_large")

const val SITES_TABLE = "sites"

fun sitesSyncTable(dao: SiteDao): SyncTable = RoomSyncTable(
    name = SITES_TABLE,
    serializer = SiteDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

class SupabaseSiteRepository(
    private val supabase: SupabaseProvider,
    private val dao: SiteDao,
    private val sync: SyncEngine,
) : SiteRepository {
    override fun observeMine(): Flow<Site?> = dao.observeMine().map { it?.toModel() }

    override suspend fun fetchMine(): AppResult<Site?> = remoteCall {
        val client = supabase.require()
        val uid = client.auth.currentUserOrNull()?.id ?: error("no session")
        val row = client.from(SITES_TABLE).select {
            filter {
                eq("agent_id", uid)
                exact("deleted_at", null)
            }
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
        if (row != null) sync.applyLocal(SITES_TABLE, listOf(row))
        row?.let { RowJson.decodeFromJsonElement(SiteDto.serializer(), it).toEntity().toModel() }
    }

    override suspend fun isSlugAvailable(slug: String): AppResult<Boolean> = remoteCall {
        supabase.require().postgrest.rpc("is_slug_available", buildJsonObject { put("slug", slug) })
            .decodeAs<Boolean>()
    }

    override suspend fun create(site: NewSite): AppResult<Site> = remoteCall {
        val client = supabase.require()
        val uid = client.auth.currentUserOrNull()?.id ?: error("no session")
        val row = client.from(SITES_TABLE).insert(
            buildJsonObject {
                put("agent_id", uid)
                put("slug", site.slug)
                put("template_key", site.templateKey)
                put("accent", site.accent)
                site.locale?.let { put("locale", it) }
                put("draft_content", site.draftContent)
            },
        ) { select() }.decodeSingle<JsonObject>()
        sync.applyLocal(SITES_TABLE, listOf(row))
        sync.refresh(SITES_TABLE)
        RowJson.decodeFromJsonElement(SiteDto.serializer(), row).toEntity().toModel()
    }

    override suspend fun publish(): AppResult<PublishOutcome> {
        val result = remoteCall {
            supabase.require().postgrest.rpc("publish_site").decodeAs<JsonObject>()
        }
        if (result is Result.Err) return result
        val outcome = parsePublishResult((result as Result.Ok).value)
        if (outcome is PublishOutcome.Published) sync.refresh(SITES_TABLE)
        return Result.Ok(outcome)
    }

    override suspend fun saveDraft(content: JsonObject): AppResult<Site> {
        if (!SiteContent.fitsSizeCap(content)) return Result.Err(ContentTooLarge)
        return updateMine(buildJsonObject { put("draft_content", content) })
    }

    override suspend fun updateAppearance(appearance: SiteAppearance): AppResult<Site> = updateMine(
        buildJsonObject {
            put("template_key", appearance.templateKey)
            put("accent", appearance.accent)
            put("locale", appearance.locale)
        },
    )

    override suspend fun changeSlug(slug: String): AppResult<Site> = updateMine(buildJsonObject { put("slug", slug) })

    /** Updates the agent's one site (column grants allow these columns; docs/BACKEND.md), then the cache. */
    private suspend fun updateMine(values: JsonObject): AppResult<Site> = remoteCall {
        val client = supabase.require()
        val uid = client.auth.currentUserOrNull()?.id ?: error("no session")
        val row = client.from(SITES_TABLE).update(values) {
            select()
            filter {
                eq("agent_id", uid)
                exact("deleted_at", null)
            }
        }.decodeSingle<JsonObject>()
        sync.applyLocal(SITES_TABLE, listOf(row))
        sync.refresh(SITES_TABLE)
        RowJson.decodeFromJsonElement(SiteDto.serializer(), row).toEntity().toModel()
    }
}

/** `{ok:true, published_at}` or `{ok:false, errors:[{section, field, item, rule}]}`. */
fun parsePublishResult(json: JsonObject): PublishOutcome {
    val ok = (json["ok"] as? JsonPrimitive)?.booleanOrNull == true
    return if (ok) {
        val at = (json["published_at"] as? JsonPrimitive)?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
        PublishOutcome.Published(at)
    } else {
        val errors = (json["errors"] as? JsonArray)?.let {
            runCatching { RowJson.decodeFromJsonElement(ListSerializer(PublishError.serializer()), it) }.getOrNull()
        }.orEmpty()
        PublishOutcome.Invalid(errors)
    }
}

/** Addresses of the free link (docs/UX.md, Link & domain): `agentpro.site/a/{slug}`, host from `SITE_BASE_URL`. */
object SiteLinks {
    /** The full URL, e.g. `https://agentpro.site/a/ramesh`; a path only when SITE_BASE_URL isn't configured. */
    fun url(slug: String, base: String = AppConfig.SITE_BASE_URL): String = base.trim().trimEnd('/') + "/a/" + slug

    /** The short display form without the scheme, e.g. `agentpro.site/a/ramesh`. */
    fun display(slug: String, base: String = AppConfig.SITE_BASE_URL): String =
        url(slug, base).removePrefix("https://").removePrefix("http://")

    /**
     * Website tab → Add to home screen (DECISIONS #65): the site's app start page with `?install=1`,
     * which opens the install sheet and isn't counted as a view.
     */
    fun installUrl(slug: String, base: String = AppConfig.SITE_BASE_URL): String = url(slug, base) + "/?install=1"
}
