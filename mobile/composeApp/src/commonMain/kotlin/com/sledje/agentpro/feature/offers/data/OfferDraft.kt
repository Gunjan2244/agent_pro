package com.sledje.agentpro.feature.offers.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** An `offerRef` (`{"source": "company"|"agent", "id": "<uuid>"}`; docs/OFFERS.md, Site schema changes). */
data class OfferRef(val source: String, val id: String) {
    fun toJson(): JsonObject = JsonObject(mapOf("source" to JsonPrimitive(source), "id" to JsonPrimitive(id)))

    companion object {
        const val COMPANY = "company"
        const val AGENT = "agent"

        fun company(id: String) = OfferRef(COMPANY, id)

        fun agent(id: String) = OfferRef(AGENT, id)

        /** Null for anything that isn't a well-formed ref. */
        fun parse(e: JsonElement?): OfferRef? {
            val o = e as? JsonObject ?: return null
            val source = (o["source"] as? JsonPrimitive)?.contentOrNull ?: return null
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            return if (source == COMPANY || source == AGENT) OfferRef(source, id) else null
        }
    }
}

/** One of the agent's own offers (`offers.custom[]`). */
data class CustomOffer(val id: String, val title: String, val summary: String, val endsOn: LocalDate?, val interest: String?, val confirmed: Boolean)

/**
 * Pure reads and edits of the site draft for the offer picker (`offers.items`, `offers.custom`, `hero.featuredOffer`).
 * Only these keys change; everything else in the draft is kept as it was.
 */
object OfferDraft {
    /** A copy's end date is at most this many days ahead (`offers.custom[].endsOn` `maxDaysAhead`). */
    const val MAX_DAYS_AHEAD = 366

    /** `offers.custom` has `maxItems` 6 (site schema). */
    const val MAX_CUSTOM = 6

    fun items(content: JsonObject): List<OfferRef> =
        (section(content, "offers")["items"] as? JsonArray).orEmpty().mapNotNull(OfferRef::parse)

    fun featured(content: JsonObject): OfferRef? = OfferRef.parse(section(content, "hero")["featuredOffer"])

    fun custom(content: JsonObject): List<CustomOffer> =
        (section(content, "offers")["custom"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
            val id = str("id") ?: return@mapNotNull null
            CustomOffer(
                id, str("title").orEmpty(), str("summary").orEmpty(),
                str("endsOn")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, str("interest"),
                (o["confirmed"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }

    /** [content] with `offers.items` = [refs]; none removes the key (the site then shows the automatic offers, DECISIONS #79). */
    fun withItems(content: JsonObject, refs: List<OfferRef>): JsonObject =
        withField(content, "offers", "items", refs.takeIf { it.isNotEmpty() }?.let { JsonArray(it.map(OfferRef::toJson)) })

    /** [content] with `hero.featuredOffer` = [ref], or removed when null. */
    fun withFeatured(content: JsonObject, ref: OfferRef?): JsonObject = withField(content, "hero", "featuredOffer", ref?.toJson())

    /**
     * The agent's own copy of [offer] (DECISIONS #104): a new `offers.custom` item with [newId], the public title and summary
     * in [locale], `endsOn` = the offer's last day capped at [today] + 366 days, `ctaKind` "enquire", the same `interest`
     * and `confirmed` false (the agent must tick it before publishing). Its company ref in `offers.items` becomes the agent
     * ref at the same position, and so does `hero.featuredOffer` when it pointed at it. Null when `offers.custom` is full.
     */
    fun copyToCustom(content: JsonObject, offer: CompanyOffer, locale: String, endsOn: LocalDate?, today: LocalDate, newId: String): JsonObject? {
        val existing = (section(content, "offers")["custom"] as? JsonArray).orEmpty()
        if (existing.size >= MAX_CUSTOM) return null
        val cap = today.plus(DatePeriod(days = MAX_DAYS_AHEAD))
        val end = listOfNotNull(endsOn, cap).min()
        val item = buildMap<String, JsonElement> {
            put("id", JsonPrimitive(newId))
            put("title", JsonPrimitive(offer.title(locale)))
            offer.summary(locale).takeIf { it.isNotBlank() }?.let { put("summary", JsonPrimitive(it)) }
            put("endsOn", JsonPrimitive(end.toString()))
            put("ctaKind", JsonPrimitive("enquire"))
            offer.interest?.let { put("interest", JsonPrimitive(it)) }
            put("confirmed", JsonPrimitive(false))
        }
        val from = OfferRef.company(offer.id)
        val to = OfferRef.agent(newId)
        var out = withField(content, "offers", "custom", JsonArray(existing + JsonObject(item)))
        out = withItems(out, items(content).map { if (it == from) to else it })
        if (featured(content) == from) out = withFeatured(out, to)
        return out
    }

    private fun section(content: JsonObject, key: String): JsonObject = content[key] as? JsonObject ?: JsonObject(emptyMap())

    private fun withField(content: JsonObject, sectionKey: String, field: String, value: JsonElement?): JsonObject {
        val s = section(content, sectionKey).toMutableMap()
        if (value == null) s.remove(field) else s[field] = value
        return JsonObject(content.toMutableMap().apply { put(sectionKey, JsonObject(s)) })
    }
}
