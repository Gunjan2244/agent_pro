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
 * One official plan the agent may show on their website (DECISIONS #129, #130): a `my_company_plans().plans` row. Facts
 * the insurer publishes, its key features (the insurer's words, or the agent's own), and its page on the site.
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
    /** The plan's address on the site, /insurance/<interest>/<slug> (DECISIONS #130). */
    val slug: String? = null,
    /** The admin's picture (https), or null: sites and the app show the default picture of the plan type. */
    val imageUrl: String? = null,
    /** The insurer's key features, copied word for word by the admin: every agent's default (0–5 points). */
    val features: List<String> = emptyList(),
    /** The agent's own wording for their site, or null: the site shows [features]. */
    val ownFeatures: List<String>? = null,
)

/**
 * The whole `my_company_plans()` answer. [auto] = no choice made: the site shows every plan in [plans]. Otherwise the
 * site shows [selected] (ids still available, in the agent's order; empty = no plans on the site).
 */
data class PlanChoice(
    val auto: Boolean,
    val selected: List<String>,
    val plans: List<CompanyPlan>,
    /** No featured choice: Home shows the first 6 plans the site shows (DECISIONS #130). */
    val featuredAuto: Boolean = true,
    /** The plans the agent features on Home, in order (at most [MAX_FEATURED]). */
    val featured: List<String> = emptyList(),
) {
    companion object {
        const val MAX_FEATURED = 6
        const val MAX_FEATURES = 5
        const val FEATURE_MIN = 3
        const val FEATURE_MAX = 400
    }
}

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
            slug = o.str("slug"),
            imageUrl = o.str("image_url"),
            features = o.strings("features").orEmpty(),
            ownFeatures = o.strings("own_features"),
        )
    }

    fun choice(answer: JsonObject): PlanChoice {
        val plans = (answer["plans"] as? JsonArray).orEmpty().mapNotNull(::plan)
        val selected = (answer["selected"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val featured = (answer["featured_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return PlanChoice(
            auto = answer.str("choice") != "custom", selected = selected, plans = plans,
            featuredAuto = answer.str("featured") != "custom", featured = featured,
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.strings(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }
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

    /** `set_featured_plans(plan_ids)`: null = automatic (the first 6), empty = none, else up to 6 in this order. */
    suspend fun saveFeatured(planIds: List<String>?): AppResult<PlanChoice>

    /** `set_plan_features(plan_id, features)`: the agent's own wording; null = back to the insurer's wording. */
    suspend fun saveFeatures(planId: String, features: List<String>?): AppResult<PlanChoice>
}

class SupabasePlansRepository(private val supabase: SupabaseProvider) : PlansRepository {
    override suspend fun load(): AppResult<PlanChoice> =
        when (val r = remoteCall { supabase.require().postgrest.rpc("my_company_plans").decodeAs<JsonObject>() }) {
            is Result.Ok -> Result.Ok(PlanJson.choice(r.value))
            is Result.Err -> r
        }

    override suspend fun save(planIds: List<String>?): AppResult<PlanChoice> {
        return call("set_site_plans", buildJsonObject { put("plan_ids", planIds.toJson()) })
    }

    override suspend fun saveFeatured(planIds: List<String>?): AppResult<PlanChoice> =
        call("set_featured_plans", buildJsonObject { put("plan_ids", planIds.toJson()) })

    override suspend fun saveFeatures(planId: String, features: List<String>?): AppResult<PlanChoice> =
        call("set_plan_features", buildJsonObject { put("plan_id", JsonPrimitive(planId)); put("features", features.toJson()) })

    private fun List<String>?.toJson(): JsonElement = this?.let { list -> buildJsonArray { list.forEach { add(JsonPrimitive(it)) } } } ?: JsonNull

    private suspend fun call(fn: String, params: JsonObject): AppResult<PlanChoice> =
        when (val r = remoteCall { supabase.require().postgrest.rpc(fn, params).decodeAs<JsonObject>() }) {
            is Result.Ok -> Result.Ok(PlanJson.choice(r.value))
            is Result.Err -> r
        }
}

/** Tests and previews that don't care: always offline. */
object NoPlansRepository : PlansRepository {
    override suspend fun load(): AppResult<PlanChoice> = Result.Err(AppError.Offline)

    override suspend fun save(planIds: List<String>?): AppResult<PlanChoice> = Result.Err(AppError.Offline)

    override suspend fun saveFeatured(planIds: List<String>?): AppResult<PlanChoice> = Result.Err(AppError.Offline)

    override suspend fun saveFeatures(planId: String, features: List<String>?): AppResult<PlanChoice> = Result.Err(AppError.Offline)
}
