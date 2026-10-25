package com.sledje.agentpro.feature.offers.data

import com.sledje.agentpro.core.catalog.InsuranceType
import com.sledje.agentpro.core.catalog.MembershipStatus
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.remoteCall
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** An insurance company from `list_companies()`; [names] is `{en, hi}` (docs/BACKEND.md, Companies and offers). */
data class Company(val id: String, val names: Map<String, String>, val logoUrl: String? = null) {
    /** The name in [locale] (a catalog locale key), else English, else any. */
    fun name(locale: String): String = names[locale]?.takeIf { it.isNotBlank() } ?: names["en"] ?: names.values.firstOrNull().orEmpty()
}

/**
 * One of the agent's company links (a `my_company_offers().memberships` row). [company] is null for a
 * "My company isn't listed" request, which carries [suggestedName] until an admin links it.
 */
data class Membership(
    val id: String,
    /** A `catalog.membershipStatuses` key. */
    val status: String,
    val company: Company?,
    val suggestedName: String?,
    val agencyCode: String?,
    /** `catalog.insuranceTypes` keys. */
    val insuranceTypes: List<String>,
    /** A `catalog.membershipRejectReasons` key. */
    val rejectReason: String?,
    val reviewNote: String?,
    val requestedAt: String?,
    val reviewedAt: String?,
) {
    val statusEntry: MembershipStatus? get() = MembershipStatus.fromKey(status)

    /** Pending or approved: the links that count and that can be edited or removed. */
    val isActive: Boolean get() = status == MembershipStatus.Pending.key || status == MembershipStatus.Approved.key

    /** The company's name in [locale], or the name the agent typed for a company that isn't listed. */
    fun displayName(locale: String): String = company?.name(locale) ?: suggestedName.orEmpty()

    /** The types that are still in the catalog, in catalog order (an unknown key from a newer server is skipped). */
    val types: List<InsuranceType> get() = InsuranceType.active.filter { it.key in insuranceTypes }
}

/**
 * One live company offer from `my_company_offers().offers` (docs/BACKEND.md). [title] and [summary] are the public
 * texts per locale (`content.<locale>.title` / `.summary`); the company's own name is never in them (DECISIONS #77).
 */
data class CompanyOffer(
    val id: String,
    val companyId: String,
    val title: Map<String, String>,
    val summary: Map<String, String>,
    /** ISO timestamp of the end (23:59:59 Asia/Kolkata on the last day). */
    val endsAt: String?,
    /** A `catalog.interests` key. */
    val interest: String?,
    /** A `catalog.offerPhases` key (`upcoming`, `running`; `ended` only if the clock moved since the answer). */
    val phase: String?,
) {
    fun title(locale: String): String = pick(title, locale)

    fun summary(locale: String): String = pick(summary, locale)

    private fun pick(texts: Map<String, String>, locale: String): String =
        texts[locale]?.takeIf { it.isNotBlank() } ?: texts["en"]?.takeIf { it.isNotBlank() } ?: texts.values.firstOrNull { it.isNotBlank() }.orEmpty()
}

/** The whole `my_company_offers()` answer: the agent's company links and the live offers of the approved ones. */
data class LiveOffers(val memberships: List<Membership>, val offers: List<CompanyOffer>) {
    /** Companies the agent is approved with (the only ones whose offers can be placed). */
    val approved: List<Membership> get() = memberships.filter { it.status == MembershipStatus.Approved.key }

    /** The name of the company that owns [offer], in [locale] (shown only inside the app, never sent to the site). */
    fun companyName(offer: CompanyOffer, locale: String): String? =
        memberships.firstOrNull { it.company?.id == offer.companyId }?.company?.name(locale)?.takeIf { it.isNotBlank() }
}

/** Where `my_company_offers` and `list_companies` JSON becomes models (pure, unit-tested). */
object CompanyJson {
    fun company(e: JsonElement?): Company? {
        val o = e as? JsonObject ?: return null
        val id = o.str("id") ?: return null
        val names = (o["name"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
        return Company(id, names, o.str("logo_url"))
    }

    fun companies(e: JsonElement?): List<Company> = (e as? JsonArray).orEmpty().mapNotNull(::company)

    fun membership(e: JsonElement?): Membership? {
        val o = e as? JsonObject ?: return null
        val id = o.str("id") ?: return null
        return Membership(
            id = id,
            status = o.str("status") ?: return null,
            company = company(o["company"]),
            suggestedName = o.str("suggested_name"),
            agencyCode = o.str("agency_code"),
            insuranceTypes = (o["insurance_types"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            rejectReason = o.str("reject_reason"),
            reviewNote = o.str("review_note"),
            requestedAt = o.str("requested_at"),
            reviewedAt = o.str("reviewed_at"),
        )
    }

    /** The `memberships` of a `my_company_offers()` answer; withdrawn rows (never sent) would be skipped too. */
    fun memberships(answer: JsonObject): List<Membership> =
        (answer["memberships"] as? JsonArray).orEmpty().mapNotNull(::membership).filter { it.status != MembershipStatus.Withdrawn.key }

    fun offer(e: JsonElement?): CompanyOffer? {
        val o = e as? JsonObject ?: return null
        val id = o.str("id") ?: return null
        val companyId = o.str("company_id") ?: return null
        val content = o["content"] as? JsonObject
        fun texts(key: String): Map<String, String> =
            content?.mapNotNull { (locale, v) -> ((v as? JsonObject)?.str(key))?.let { locale to it } }?.toMap().orEmpty()
        return CompanyOffer(id, companyId, texts("title"), texts("summary"), o.str("ends_at"), o.str("interest"), o.str("phase"))
    }

    /** The whole answer: memberships (withdrawn skipped) and offers (rows without an id or company skipped). */
    fun liveOffers(answer: JsonObject): LiveOffers =
        LiveOffers(memberships(answer), (answer["offers"] as? JsonArray).orEmpty().mapNotNull(::offer))

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}

/**
 * The agent's company links (DECISIONS #89, docs/OFFERS.md, App). Remote only: every change goes through an RPC and
 * then [refresh]es [memberships], which holds the last answer (empty until the first one). The Room snapshot planned
 * in OFFERS.md arrives with the offers screens.
 */
interface CompanyRepository {
    /** The agent's links except withdrawn ones, oldest request first. */
    val memberships: StateFlow<List<Membership>>

    /** Forgets the cached list (sign-out), so the next agent never sees it. */
    fun clear()

    /** `my_company_offers()`; updates [memberships]. Works on a lapsed plan. */
    suspend fun refresh(): AppResult<Unit>

    /**
     * `my_company_offers()` in full, for the offer picker (OFFERS.md, Place offers); also updates [memberships]. Online
     * only: nothing is cached. The default (for test fakes) is an unknown error.
     */
    suspend fun liveOffers(): AppResult<LiveOffers> = Result.Err(AppError.Unknown("liveOffers"))

    /** `list_companies()`: every active company, in the server's order. */
    suspend fun listCompanies(): AppResult<List<Company>>

    /** `request_company`: [companyId] with [agencyCode], or [suggestedName] for a company that isn't listed. */
    suspend fun request(companyId: String?, suggestedName: String?, agencyCode: String?, insuranceTypes: List<String>): AppResult<Membership>

    /** `withdraw_company_request`: cancels a pending request or removes an approved link. */
    suspend fun withdraw(membershipId: String): AppResult<Unit>

    /** `set_membership_types`: no new review. */
    suspend fun setTypes(membershipId: String, insuranceTypes: List<String>): AppResult<Membership>
}

class SupabaseCompanyRepository(private val supabase: SupabaseProvider) : CompanyRepository {
    private val _memberships = MutableStateFlow<List<Membership>>(emptyList())
    override val memberships: StateFlow<List<Membership>> = _memberships.asStateFlow()

    override fun clear() { _memberships.value = emptyList() }

    override suspend fun refresh(): AppResult<Unit> {
        val result = remoteCall { supabase.require().postgrest.rpc("my_company_offers").decodeAs<JsonObject>() }
        return when (result) {
            is Result.Ok -> { _memberships.value = CompanyJson.memberships(result.value); Result.Ok(Unit) }
            is Result.Err -> result
        }
    }

    override suspend fun liveOffers(): AppResult<LiveOffers> {
        val result = remoteCall { supabase.require().postgrest.rpc("my_company_offers").decodeAs<JsonObject>() }
        return when (result) {
            is Result.Ok -> {
                val live = CompanyJson.liveOffers(result.value)
                _memberships.value = live.memberships
                Result.Ok(live)
            }
            is Result.Err -> result
        }
    }

    override suspend fun listCompanies(): AppResult<List<Company>> {
        val result = remoteCall { supabase.require().postgrest.rpc("list_companies").decodeAs<JsonArray>() }
        return when (result) {
            is Result.Ok -> Result.Ok(CompanyJson.companies(result.value))
            is Result.Err -> result
        }
    }

    override suspend fun request(companyId: String?, suggestedName: String?, agencyCode: String?, insuranceTypes: List<String>): AppResult<Membership> =
        write("request_company") {
            put("company_id", companyId)
            put("suggested_name", suggestedName)
            put("agency_code", agencyCode)
            put("insurance_types", typesJson(insuranceTypes))
        }

    override suspend fun withdraw(membershipId: String): AppResult<Unit> =
        when (val r = write("withdraw_company_request") { put("membership_id", membershipId) }) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }

    override suspend fun setTypes(membershipId: String, insuranceTypes: List<String>): AppResult<Membership> =
        write("set_membership_types") {
            put("membership_id", membershipId)
            put("insurance_types", typesJson(insuranceTypes))
        }

    /** Calls a write RPC, then refreshes the list ("after each write"); a failed refresh doesn't fail the write. */
    private suspend fun write(rpc: String, params: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): AppResult<Membership> {
        val result = remoteCall { supabase.require().postgrest.rpc(rpc, buildJsonObject(params)).decodeAs<JsonObject>() }
        return when (result) {
            is Result.Ok -> {
                refresh()
                CompanyJson.membership(result.value)?.let { Result.Ok(it) } ?: Result.Err(AppError.Unknown("membership"))
            }
            is Result.Err -> result
        }
    }

    private fun typesJson(types: List<String>): JsonArray = buildJsonArray { types.forEach { add(JsonPrimitive(it)) } }
}
