package com.sledje.agentpro.feature.portal.data

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.remoteCall
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** `portal_status().state`: where a customer is in the website login (docs/BACKEND.md, Customer portal). */
enum class PortalState(val key: String) {
    None("none"),
    Invited("invited"),
    Active("active"),
    Locked("locked"),
    ;

    companion object {
        /** An unknown state from a newer server reads as [None]. */
        fun fromKey(key: String?): PortalState = entries.firstOrNull { it.key == key } ?: None
    }
}

/** A `portal_status(customer_id)` answer. */
data class PortalStatus(
    val state: PortalState,
    val inviteExpiresAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val lockedUntil: Instant? = null,
    /** Names of the other customers (same mobile number) attached to the same login. */
    val sharedWith: List<String> = emptyList(),
    /** False when the customer login can't be reached on the website (flag, toggle, unpublished or lapsed); a missing value reads true. */
    val portalAvailable: Boolean = true,
) {
    companion object {
        val None = PortalStatus(PortalState.None)
    }
}

/** A `portal_invite(customer_id)` answer. */
sealed interface InviteResult {
    /** [token] is shown once: it goes into the link and is never stored. */
    data class Created(val token: String, val expiresAt: Instant?, val alreadyActive: Boolean) : InviteResult

    /** The customer's mobile number isn't a valid Indian mobile. */
    data object InvalidPhone : InviteResult

    data object NoSite : InviteResult

    /** The `customerPortal` flag is off on the server. */
    data object Unavailable : InviteResult
}

/** Where `portal_status` and `portal_invite` JSON become models (pure, unit-tested). */
object PortalJson {
    fun status(e: JsonElement?): PortalStatus? {
        val o = e as? JsonObject ?: return null
        return PortalStatus(
            state = PortalState.fromKey(o.str("state")),
            inviteExpiresAt = o.instant("invite_expires_at"),
            lastLoginAt = o.instant("last_login_at"),
            lockedUntil = o.instant("locked_until"),
            sharedWith = (o["shared_with"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull },
            portalAvailable = (o["portal_available"] as? JsonPrimitive)?.booleanOrNull ?: true,
        )
    }

    fun invite(e: JsonElement?): InviteResult? {
        val o = e as? JsonObject ?: return null
        val ok = (o["ok"] as? JsonPrimitive)?.booleanOrNull ?: return null
        if (ok) {
            val token = o.str("token")?.takeIf { it.isNotBlank() } ?: return null
            val alreadyActive = (o["already_active"] as? JsonPrimitive)?.booleanOrNull ?: false
            return InviteResult.Created(token, o.instant("expires_at"), alreadyActive)
        }
        return when (o.str("error")) {
            "invalid" -> InviteResult.InvalidPhone
            "no_site" -> InviteResult.NoSite
            "unavailable" -> InviteResult.Unavailable
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.instant(key: String): Instant? = str(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

/** The share link a customer opens to set a PIN: `<site>/a/<slug>/my/start?t=<token>` (customer portal contract, D). */
object PortalLinks {
    fun startUrl(siteUrl: String, token: String): String = siteUrl.trimEnd('/') + "/my/start?t=" + token
}

/**
 * The agent's side of the customer portal (docs/BACKEND.md, Customer portal). Online only: every call is an RPC, and
 * nothing is stored in Room.
 */
interface PortalRepository {
    /** `portal_status(customer_id)`. */
    suspend fun status(customerId: String): AppResult<PortalStatus>

    /** `portal_invite(customer_id)`: a new login link; an active customer's PIN is reset. */
    suspend fun invite(customerId: String): AppResult<InviteResult>

    /** `portal_revoke(customer_id)`: turns the login off and ends its sessions. */
    suspend fun revoke(customerId: String): AppResult<Unit>
}

class SupabasePortalRepository(private val supabase: SupabaseProvider) : PortalRepository {
    override suspend fun status(customerId: String): AppResult<PortalStatus> =
        parsed(call("portal_status", customerId), PortalJson::status)

    override suspend fun invite(customerId: String): AppResult<InviteResult> =
        parsed(call("portal_invite", customerId), PortalJson::invite)

    override suspend fun revoke(customerId: String): AppResult<Unit> =
        when (val r = call("portal_revoke", customerId)) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }

    private suspend fun call(rpc: String, customerId: String): AppResult<JsonObject> = remoteCall {
        supabase.require().postgrest.rpc(rpc, buildJsonObject { put("customer_id", customerId) }).decodeAs<JsonObject>()
    }

    private fun <T> parsed(r: AppResult<JsonObject>, parse: (JsonElement?) -> T?): AppResult<T> = when (r) {
        is Result.Ok -> parse(r.value)?.let { Result.Ok(it) } ?: Result.Err(AppError.Unknown("portal"))
        is Result.Err -> r
    }
}

/** What [com.sledje.agentpro.app.AppDependencies.portal] is until a real repository is wired (tests): always offline. */
object NoPortalRepository : PortalRepository {
    override suspend fun status(customerId: String): AppResult<PortalStatus> = Result.Err(AppError.Offline)
    override suspend fun invite(customerId: String): AppResult<InviteResult> = Result.Err(AppError.Offline)
    override suspend fun revoke(customerId: String): AppResult<Unit> = Result.Err(AppError.Offline)
}
