package com.sledje.agentpro.feature.offers.data

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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * One official plan the agent may show on their website (DECISIONS #129): a `my_company_plans().plans` row. Only facts
 * the insurer publishes; the site links to [url] on the insurer's own website.
 */
data class CompanyPlan(
    val id: String,
    val companyId: String,
    /** The company's name in the site's language. */
    val company: String,
    val name: String,
    /** LIC's plan number, when the insurer gives one. */
    val planNo: String?,
    val uin: String?,
    /** A `catalog.interests` key (the plan type). */
    val interest: String,
    val url: String,
)

/**
 * The whole `my_company_plans()` answer. [auto] = no choice made: the site shows every plan in [plans]. Otherwise the
 * site shows [selected] (ids still available, in the agent's order; empty = no plans on the site).
 */
data class PlanChoice(val auto: Boolean, val selected: List<String>, val plans: List<CompanyPlan>)

/** Where `my_company_plans` JSON becomes models (pure). */
object PlanJson {
    fun plan(e: JsonElement?): CompanyPlan? {
        val o = e as? JsonObject ?: return null
        return CompanyPlan(
            id = o.str("id") ?: return null,
            companyId = o.str("company_id") ?: return null,
            company = o.str("company").orEmpty(),
            name = o.str("name") ?: return null,
            planNo = o.str("plan_no"),
            uin = o.str("uin"),
            interest = o.str("interest") ?: return null,
            url = o.str("url") ?: return null,
        )
    }

    fun choice(answer: JsonObject): PlanChoice {
        val plans = (answer["plans"] as? JsonArray).orEmpty().mapNotNull(::plan)
        val selected = (answer["selected"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return PlanChoice(auto = answer.str("choice") != "custom", selected = selected, plans = plans)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}

/**
 * The agent's choice of official plans for their website (DECISIONS #129; docs/OFFERS.md, Plans). Online only, like the
 * offer picker: nothing is cached. The live site follows a save at once (plans resolve on every page load).
 */
interface PlansRepository {
    /** `my_company_plans()`. */
    suspend fun load(): AppResult<PlanChoice>

    /** `set_site_plans(plan_ids)`: null = automatic (every plan), empty = none, else these plans in this order. */
    suspend fun save(planIds: List<String>?): AppResult<PlanChoice>
}

class SupabasePlansRepository(private val supabase: SupabaseProvider) : PlansRepository {
    override suspend fun load(): AppResult<PlanChoice> =
        when (val r = remoteCall { supabase.require().postgrest.rpc("my_company_plans").decodeAs<JsonObject>() }) {
            is Result.Ok -> Result.Ok(PlanJson.choice(r.value))
            is Result.Err -> r
        }

    override suspend fun save(planIds: List<String>?): AppResult<PlanChoice> {
        val ids: JsonElement = planIds?.let { list -> buildJsonArray { list.forEach { add(JsonPrimitive(it)) } } } ?: JsonNull
        val params = buildJsonObject { put("plan_ids", ids) }
        return when (val r = remoteCall { supabase.require().postgrest.rpc("set_site_plans", params).decodeAs<JsonObject>() }) {
            is Result.Ok -> Result.Ok(PlanJson.choice(r.value))
            is Result.Err -> r
        }
    }
}

/** Tests and previews that don't care: always offline. */
object NoPlansRepository : PlansRepository {
    override suspend fun load(): AppResult<PlanChoice> = Result.Err(AppError.Offline)

    override suspend fun save(planIds: List<String>?): AppResult<PlanChoice> = Result.Err(AppError.Offline)
}
