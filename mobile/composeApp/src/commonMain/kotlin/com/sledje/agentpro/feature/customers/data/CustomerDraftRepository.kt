package com.sledje.agentpro.feature.customers.data

import com.sledje.agentpro.core.data.AppError
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
import com.sledje.agentpro.core.data.updateRow
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

const val CUSTOMER_DRAFTS_TABLE = "customer_drafts"

/** A `customer_signup_link()` or `customer_signup_renew()` answer (docs/BACKEND.md, Customer sign-up link). */
sealed interface SignupLinkResult {
    /** [token] is shown once: it goes into the link and is never stored. */
    data class Created(val draftId: String, val token: String, val expiresAt: Instant?) : SignupLinkResult

    /** The `customerSignup` flag is off on the server. */
    data object Unavailable : SignupLinkResult

    /** No published website to host the page. */
    data object NoSite : SignupLinkResult

    /** More than `CustomerSignup.openLinksPerAgent` links are open. */
    data object TooMany : SignupLinkResult
}

/** Where the sign-up RPC JSON becomes models (pure, unit-tested). */
object CustomerSignupJson {
    fun link(e: JsonElement?): SignupLinkResult? {
        val o = e as? JsonObject ?: return null
        val ok = (o["ok"] as? JsonPrimitive)?.booleanOrNull ?: return null
        if (ok) {
            val draftId = o.str("draft_id")?.takeIf { it.isNotBlank() } ?: return null
            val token = o.str("token")?.takeIf { it.isNotBlank() } ?: return null
            return SignupLinkResult.Created(draftId, token, o.str("expires_at")?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }
        return when (o.str("error")) {
            "unavailable" -> SignupLinkResult.Unavailable
            "no_site" -> SignupLinkResult.NoSite
            "too_many" -> SignupLinkResult.TooMany
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}

/** The link a customer opens: `<site>/join?t=<token>` (the Worker moves the token into a cookie). */
object SignupLinks {
    fun joinUrl(siteUrl: String, token: String): String = siteUrl.trimEnd('/') + "/join?t=" + token
}

/**
 * Unfinished customer forms (docs/BACKEND.md, `customer_drafts`): the agent's own autosaved forms and the
 * customer sign-up links. Reads come from Room; the agent's draft writes go to Supabase first, and the link
 * RPCs are online only.
 */
interface CustomerDraftRepository {
    /** Drafts neither completed nor deleted, newest first. */
    fun observeIncomplete(): Flow<List<CustomerDraft>>

    /** One draft (completed or not), or null when it is gone. */
    fun observe(id: String): Flow<CustomerDraft?>

    /** Inserts a `source = agent` draft with the typed [fields]. */
    suspend fun createAgentDraft(fields: Map<String, String>): AppResult<CustomerDraft>

    suspend fun updateAgentDraft(id: String, fields: Map<String, String>): AppResult<CustomerDraft>

    /** The agent saved the customer: marks the draft completed (`completed_at`, `customer_id`, `fields = {}`). */
    suspend fun complete(id: String, customerId: String): AppResult<CustomerDraft>

    /** Soft delete (sets `deleted_at`); [restore] is the Undo. */
    suspend fun delete(id: String): AppResult<Unit>

    suspend fun restore(id: String): AppResult<CustomerDraft>

    /** `customer_signup_link()`: a new sign-up link. */
    suspend fun createLink(): AppResult<SignupLinkResult>

    /** `customer_signup_renew(draft_id)`: a new token and expiry for an open link. */
    suspend fun renewLink(draftId: String): AppResult<SignupLinkResult>

    suspend fun refresh(): AppResult<Unit>
}

fun customerDraftsSyncTable(dao: CustomerDraftDao): SyncTable = RoomSyncTable(
    name = CUSTOMER_DRAFTS_TABLE,
    serializer = CustomerDraftDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

class SupabaseCustomerDraftRepository(
    private val supabase: SupabaseProvider,
    private val dao: CustomerDraftDao,
    private val sync: SyncEngine,
    private val clock: Clock = Clock.System,
) : CustomerDraftRepository {
    override fun observeIncomplete(): Flow<List<CustomerDraft>> =
        dao.observeIncomplete().map { rows -> rows.map { it.toModel() }.sortedByDescending { it.updatedAt } }

    override fun observe(id: String): Flow<CustomerDraft?> = dao.observe(id).map { it?.toModel() }

    override suspend fun createAgentDraft(fields: Map<String, String>): AppResult<CustomerDraft> = write {
        supabase.insertRow(
            CUSTOMER_DRAFTS_TABLE,
            buildJsonObject {
                put("source", DraftSource.Agent.key)
                put("fields", DraftJson.toJson(fields))
            },
        )
    }

    override suspend fun updateAgentDraft(id: String, fields: Map<String, String>): AppResult<CustomerDraft> =
        write { supabase.updateRow(CUSTOMER_DRAFTS_TABLE, id, buildJsonObject { put("fields", DraftJson.toJson(fields)) }) }

    override suspend fun complete(id: String, customerId: String): AppResult<CustomerDraft> = write {
        supabase.updateRow(
            CUSTOMER_DRAFTS_TABLE,
            id,
            buildJsonObject {
                put("completed_at", clock.now().toString())
                put("customer_id", customerId)
                put("fields", JsonObject(emptyMap()))
            },
        )
    }

    override suspend fun delete(id: String): AppResult<Unit> =
        write { supabase.updateRow(CUSTOMER_DRAFTS_TABLE, id, buildJsonObject { put("deleted_at", clock.now().toString()) }) }.map { }

    override suspend fun restore(id: String): AppResult<CustomerDraft> =
        write { supabase.updateRow(CUSTOMER_DRAFTS_TABLE, id, buildJsonObject { put("deleted_at", JsonNull) }) }

    override suspend fun createLink(): AppResult<SignupLinkResult> = link { supabase.require().postgrest.rpc("customer_signup_link").decodeAs<JsonObject>() }

    override suspend fun renewLink(draftId: String): AppResult<SignupLinkResult> = link {
        supabase.require().postgrest.rpc("customer_signup_renew", buildJsonObject { put("draft_id", draftId) }).decodeAs<JsonObject>()
    }

    override suspend fun refresh(): AppResult<Unit> = sync.refresh(CUSTOMER_DRAFTS_TABLE).map { }

    private suspend fun link(call: suspend () -> JsonObject): AppResult<SignupLinkResult> {
        val result = remoteCall { call() }
        if (result is Result.Err) return result
        val parsed = CustomerSignupJson.link((result as Result.Ok).value) ?: return Result.Err(AppError.Unknown("customer_signup"))
        // Best effort: the new row also arrives through realtime.
        if (parsed is SignupLinkResult.Created) sync.refresh(CUSTOMER_DRAFTS_TABLE)
        return Result.Ok(parsed)
    }

    private suspend fun write(block: suspend () -> JsonObject): AppResult<CustomerDraft> {
        val result = remoteCall { block() }
        if (result is Result.Err) return result
        val row = (result as Result.Ok).value
        sync.applyLocal(CUSTOMER_DRAFTS_TABLE, listOf(row))
        return Result.Ok(row.decodeRow(CustomerDraftDto.serializer()).toEntity().toModel())
    }
}

/** What [com.sledje.agentpro.app.AppDependencies.customerDrafts] is until a real repository is wired (tests): nothing, offline. */
object NoCustomerDraftRepository : CustomerDraftRepository {
    override fun observeIncomplete(): Flow<List<CustomerDraft>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observe(id: String): Flow<CustomerDraft?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun createAgentDraft(fields: Map<String, String>): AppResult<CustomerDraft> = Result.Err(AppError.Offline)
    override suspend fun updateAgentDraft(id: String, fields: Map<String, String>): AppResult<CustomerDraft> = Result.Err(AppError.Offline)
    override suspend fun complete(id: String, customerId: String): AppResult<CustomerDraft> = Result.Err(AppError.Offline)
    override suspend fun delete(id: String): AppResult<Unit> = Result.Err(AppError.Offline)
    override suspend fun restore(id: String): AppResult<CustomerDraft> = Result.Err(AppError.Offline)
    override suspend fun createLink(): AppResult<SignupLinkResult> = Result.Err(AppError.Offline)
    override suspend fun renewLink(draftId: String): AppResult<SignupLinkResult> = Result.Err(AppError.Offline)
    override suspend fun refresh(): AppResult<Unit> = Result.Err(AppError.Offline)
}
