package com.sledje.agentpro.feature.privacy.data

import com.sledje.agentpro.app.AppConfig
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.NoticeConsent
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.remoteCall
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Consents the agent gives in the app, recorded by the `record_consent` RPC (docs/PRIVACY.md,
 * sections 1 and 3, "P1-1 contract"). The sign-up consent travels in the sign-up metadata instead
 * (AuthRepository.signUp).
 */
interface ConsentRepository {
    /** True once the agent accepted the current `agent_customer_duty` notice version on this account. */
    fun customerDutyAccepted(): Flow<Boolean>

    /**
     * Records the one-time customer-data attestation (`agent_customer_duty`, in [locale]) and remembers it
     * locally, so it shows once per account (again when the notice version changes). Needs internet.
     */
    suspend fun acceptCustomerDuty(locale: AppLocale): AppResult<Unit>

    /** Records `guardian_consent` for [customerId] (a customer under 18; in [locale]). */
    suspend fun recordGuardianConsent(customerId: String, locale: AppLocale): AppResult<Unit>
}

/** `{ok:true}` → Ok; `{ok:false, error, field}` → `Validation(field, error)` (pure, unit-tested). */
fun parseRecordConsentResult(json: JsonObject): AppResult<Unit> {
    if ((json["ok"] as? JsonPrimitive)?.booleanOrNull == true) return Result.Ok(Unit)
    val field = (json["field"] as? JsonPrimitive)?.contentOrNull ?: "consent"
    val rule = (json["error"] as? JsonPrimitive)?.contentOrNull
    return Result.Err(AppError.Validation(field, rule))
}

/** The `record_consent` arguments (pure, unit-tested). */
fun recordConsentArgs(consent: NoticeConsent, customerId: String?): JsonObject = buildJsonObject {
    put("notice_key", consent.noticeKey)
    put("version", consent.version)
    put("hash", consent.hash)
    if (customerId != null) put("customer_id", customerId)
}

/** The local mark that the attestation was accepted: one key per account, holding the notice version. */
fun customerDutyKey(userId: String): String = "privacy.customer_duty.$userId"

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseConsentRepository(
    private val supabase: SupabaseProvider,
    private val store: KeyValueStore,
    /** The signed-in agent's id, or null. */
    private val userId: () -> String?,
    /** Emits the signed-in agent's id (null when signed out); drives [customerDutyAccepted]. */
    private val userIds: Flow<String?>,
) : ConsentRepository {
    override fun customerDutyAccepted(): Flow<Boolean> = userIds.transformLatest { id ->
        if (id == null) {
            emit(false)
        } else {
            emitAll(store.observe(customerDutyKey(id)).map { it == PrivacyNotice.AgentCustomerDuty.version.toString() })
        }
    }

    override suspend fun acceptCustomerDuty(locale: AppLocale): AppResult<Unit> {
        val id = userId() ?: return Result.Err(AppError.Unauthorized)
        val result = record(PrivacyNotice.AgentCustomerDuty.consent(locale), null)
        if (result is Result.Ok) store.put(customerDutyKey(id), PrivacyNotice.AgentCustomerDuty.version.toString())
        return result
    }

    override suspend fun recordGuardianConsent(customerId: String, locale: AppLocale): AppResult<Unit> =
        record(PrivacyNotice.GuardianConsent.consent(locale), customerId)

    private suspend fun record(consent: NoticeConsent, customerId: String?): AppResult<Unit> {
        val result = remoteCall {
            supabase.require().postgrest.rpc("record_consent", recordConsentArgs(consent, customerId)).decodeAs<JsonObject>()
        }
        return when (result) {
            is Result.Ok -> parseRecordConsentResult(result.value)
            is Result.Err -> result
        }
    }
}

/** Links to the agent-facing privacy pages (docs/PRIVACY.md; the paths are served by the web renderer). */
object PrivacyLinks {
    /** The agent's site privacy page, `<SITE_BASE_URL>/a/<slug>/privacy`. */
    fun sitePrivacy(slug: String, base: String = AppConfig.SITE_BASE_URL): String = base.trim().trimEnd('/') + "/a/" + slug + "/privacy"

    /** Agent PRO's Terms page, `<SITE_BASE_URL>/terms`; null when SITE_BASE_URL isn't configured. */
    fun terms(base: String = AppConfig.SITE_BASE_URL): String? = base.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/terms" }
}
