package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.catalog.TemplateFamily
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.SectionSpec
import com.sledje.agentpro.core.forms.ValidationError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The site content document (`sites.draft_content`; docs/SHARED_DEFINITIONS.md, site schema;
 * docs/WEB_RENDERER.md, Behaviour notes): one object per section key with its field values, plus
 * `sectionOrder` (section keys) and `hidden` (section keys). Pure functions, unit-tested.
 *
 * Pinned sections (hero, contact) can't be hidden and keep their schema place: pinned sections that
 * come before every other section in the schema stay first, the rest stay last.
 */
object SiteContent {
    const val SECTION_ORDER = "sectionOrder"
    const val HIDDEN = "hidden"

    /** `sites` content columns are capped at 64 KB (`octet_length(content::text) <= 65536`, docs/BACKEND.md). */
    const val MAX_BYTES = 65536

    fun sizeBytes(content: JsonObject): Int = RowJson.encodeToString(JsonObject.serializer(), content).encodeToByteArray().size

    fun fitsSizeCap(content: JsonObject): Boolean = sizeBytes(content) <= MAX_BYTES

    // ---- Order and visibility -----------------------------------------------------------------

    /** Sections whose editor isn't built yet; the web ignores them too until it renders them (OFFERS.md, 8b-3/8b-4). */
    val pendingSections: Set<String> = setOf("offers")

    /** The sections the agent edits: the schema's, minus [pendingSections]. */
    val sections: List<SectionSpec> get() = SiteSchema.sections.filterNot { it.key in pendingSections }

    private val schema: List<SectionSpec> get() = sections

    private fun leadingPinned(sections: List<SectionSpec>): List<SectionSpec> = sections.takeWhile { it.pinned }

    /** The sections in the agent's order (unknown keys dropped, missing ones appended in schema order). */
    fun orderedSections(content: JsonObject, sections: List<SectionSpec> = schema): List<SectionSpec> {
        val byKey = sections.associateBy { it.key }
        val stored = stringList(content[SECTION_ORDER]).distinct().mapNotNull { byKey[it] }
        // A section the stored order doesn't name (one added to the schema later, e.g. `companies`) goes right after
        // the schema section before it, not at the end.
        val all = stored.toMutableList()
        sections.forEachIndexed { i, s ->
            if (s !in all) {
                val prev = sections.take(i).lastOrNull { it in all }
                all.add(if (prev == null) 0 else all.indexOf(prev) + 1, s)
            }
        }
        val head = leadingPinned(sections)
        val tail = sections.filter { it.pinned && it !in head }
        return head + all.filterNot { it.pinned } + tail
    }

    /** The sections the agent can move (everything but the pinned ones), in order. */
    fun movableSections(content: JsonObject, sections: List<SectionSpec> = schema): List<SectionSpec> =
        orderedSections(content, sections).filterNot { it.pinned }

    fun isHidden(content: JsonObject, section: SectionSpec): Boolean =
        !section.pinned && section.key in stringList(content[HIDDEN])

    /** Shows or hides [section]; a pinned section is never hidden. */
    fun withHidden(content: JsonObject, section: SectionSpec, hidden: Boolean): JsonObject {
        if (section.pinned) return content
        val current = stringList(content[HIDDEN]).toMutableList()
        current.remove(section.key)
        if (hidden) current += section.key
        return content.with(HIDDEN, JsonArray(current.map(::JsonPrimitive)))
    }

    /** Moves the movable section at [from] to [to] (indices into [movableSections]); writes the full order. */
    fun move(content: JsonObject, from: Int, to: Int, sections: List<SectionSpec> = schema): JsonObject {
        val movable = movableSections(content, sections).toMutableList()
        if (from !in movable.indices || to !in movable.indices || from == to) return content
        movable.add(to, movable.removeAt(from))
        val head = leadingPinned(sections)
        val tail = sections.filter { it.pinned && it !in head }
        val keys = (head + movable + tail).map { it.key }.toMutableList()
        // Sections the editor doesn't offer yet (offers) keep their stored place, after the section they followed.
        val stored = stringList(content[SECTION_ORDER])
        stored.forEachIndexed { i, key ->
            if (key in pendingSections && key !in keys) {
                val after = stored.getOrNull(i - 1)?.let { keys.indexOf(it) } ?: -1
                keys.add(if (after >= 0) after + 1 else minOf(i, keys.size), key)
            }
        }
        return content.with(SECTION_ORDER, JsonArray(keys.map(::JsonPrimitive)))
    }

    /**
     * Switching to an `offers`-family template (docs/UX.md, Appearance): un-hides `offers` and puts it right
     * after `hero` in the order; every other section stays where it is. [defaultOrder] (the old template's
     * sections) is used when the draft has no stored order.
     */
    fun withOffersAfterHero(content: JsonObject, defaultOrder: List<String>): JsonObject {
        val offers = "offers"
        val hidden = stringList(content[HIDDEN]).filter { it != offers }
        val order = stringList(content[SECTION_ORDER]).ifEmpty { defaultOrder }.filter { it != offers }.toMutableList()
        val hero = order.indexOf("hero")
        order.add(if (hero >= 0) hero + 1 else 0, offers)
        return content.with(HIDDEN, JsonArray(hidden.map(::JsonPrimitive))).with(SECTION_ORDER, JsonArray(order.map(::JsonPrimitive)))
    }

    /**
     * The draft as it is after switching from [from] to [to] (docs/UX.md, Appearance): to an `offers`-family
     * template from another family, `offers` is un-hidden and moved after `hero`; otherwise unchanged. Used by
     * the switch itself and by the Appearance preview, so both show the same page (DECISIONS #86).
     */
    fun afterSwitch(content: JsonObject, from: TemplateSpec?, to: TemplateSpec): JsonObject =
        if (to.family == TemplateFamily.Offers && from?.family != TemplateFamily.Offers) {
            withOffersAfterHero(content, (from ?: to).sections)
        } else {
            content
        }

    // ---- Section values <-> FormState -----------------------------------------------------------

    /** The section's stored values in FormState's types (see `FormState`): text as String, toggles Boolean, … */
    fun sectionValues(content: JsonObject, section: SectionSpec): Map<String, Any?> =
        fieldValues(content[section.key] as? JsonObject ?: JsonObject(emptyMap()), section.fields)

    private fun fieldValues(obj: JsonObject, fields: List<FieldSpec>): Map<String, Any?> = buildMap {
        fields.forEach { spec ->
            val element = obj[spec.key] ?: return@forEach
            if (element is JsonNull) return@forEach
            val value: Any? = when (spec.type) {
                FieldType.Toggle -> (element as? JsonPrimitive)?.booleanOrNull
                FieldType.MultiSelect -> stringList(element)
                FieldType.ItemList -> (element as? JsonArray).orEmpty().mapNotNull { item ->
                    (item as? JsonObject)?.let { fieldValues(it, spec.itemFields) }
                }
                FieldType.OfferRef, FieldType.OfferList -> element
                FieldType.Number -> (element as? JsonPrimitive)?.let { p ->
                    p.longOrNull?.toString() ?: p.doubleOrNull?.let { d -> if (d % 1.0 == 0.0) d.toLong().toString() else d.toString() } ?: p.contentOrNull
                }
                else -> (element as? JsonPrimitive)?.contentOrNull
            }
            if (value != null) put(spec.key, value)
        }
    }

    /**
     * [content] with [section] replaced by [values] (`FormState.values()`): blank values and empty lists are
     * left out; keys the schema doesn't know are kept as they were.
     */
    fun withSection(content: JsonObject, section: SectionSpec, values: Map<String, Any?>): JsonObject {
        val existing = content[section.key] as? JsonObject ?: JsonObject(emptyMap())
        return content.with(section.key, mergeFields(existing, section.fields, values))
    }

    private fun mergeFields(existing: JsonObject, fields: List<FieldSpec>, values: Map<String, Any?>): JsonObject {
        val out = existing.toMutableMap()
        fields.forEach { spec ->
            val json = toJson(spec, values[spec.key])
            if (json == null) out.remove(spec.key) else out[spec.key] = json
        }
        return JsonObject(out)
    }

    private fun toJson(spec: FieldSpec, value: Any?): JsonElement? = when (spec.type) {
        FieldType.Toggle -> (value as? Boolean)?.let(::JsonPrimitive)
        FieldType.Number -> when (value) {
            is Long -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value.toLong())
            is Double -> JsonPrimitive(value)
            is String -> value.trim().takeIf { it.isNotEmpty() }?.let { s -> s.toLongOrNull()?.let(::JsonPrimitive) ?: s.toDoubleOrNull()?.let(::JsonPrimitive) }
            else -> null
        }
        FieldType.OfferRef -> value as? JsonObject
        FieldType.OfferList -> (value as? JsonArray)?.takeIf { it.isNotEmpty() }
        FieldType.MultiSelect -> (value as? List<*>)?.filterIsInstance<String>()?.takeIf { it.isNotEmpty() }?.let { keys -> JsonArray(keys.map(::JsonPrimitive)) }
        FieldType.ItemList -> (value as? List<*>)?.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            (item as? Map<String, Any?>)?.let { mergeFields(JsonObject(emptyMap()), spec.itemFields, it) }
        }?.takeIf { it.isNotEmpty() }?.let(::JsonArray)
        else -> (value as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)
    }

    // ---- Summaries ----------------------------------------------------------------------------

    /** What a Content row says about a section (docs/UX.md, Content screen: "summary"). */
    sealed interface Summary {
        data class Text(val value: String) : Summary
        data class Items(val count: Int) : Summary
        data object Empty : Summary
    }

    fun summary(content: JsonObject, section: SectionSpec): Summary {
        val values = sectionValues(content, section)
        section.fields.firstOrNull { it.type == FieldType.Text || it.type == FieldType.Multiline }
            ?.let { values[it.key] as? String }?.takeIf { it.isNotBlank() }
            ?.let { return Summary.Text(it.lineSequence().first().trim()) }
        section.fields.firstOrNull { it.type == FieldType.ItemList }?.let { list ->
            val count = (values[list.key] as? List<*>)?.size ?: 0
            if (count > 0) return Summary.Items(count)
        }
        section.fields.firstOrNull { it.type == FieldType.MultiSelect }?.let { multi ->
            val count = (values[multi.key] as? List<*>)?.size ?: 0
            if (count > 0) return Summary.Items(count)
        }
        return Summary.Empty
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun stringList(element: JsonElement?): List<String> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(toMutableMap().apply { put(key, value) })
}

/** A `publish_site()` error resolved against the schema, for the Content list and the section editor. */
data class PublishIssue(
    val section: SectionSpec,
    /** The field (for a list item: the item's field). Null when the server named an unknown field. */
    val field: FieldSpec?,
    /** The list field holding [field], when the error is on a list item. */
    val listField: FieldSpec?,
    /** 0-based list item index. */
    val item: Int?,
    val error: ValidationError,
)

object PublishIssues {
    /**
     * Maps the server's `{section, field, item, rule}` errors (docs/BACKEND.md, Publish validation) to
     * schema specs and the same [ValidationError]s the app's validators use. Errors on unknown sections
     * are dropped (the schema decides what the agent can fix).
     */
    fun resolve(errors: List<PublishError>, sections: List<SectionSpec> = SiteSchema.sections): List<PublishIssue> =
        errors.mapNotNull { e ->
            val section = sections.firstOrNull { it.key == e.section } ?: return@mapNotNull null
            val listField = if (e.item != null) section.fields.firstOrNull { f -> f.type == FieldType.ItemList && f.itemFields.any { it.key == e.field } } else null
            val field = listField?.itemFields?.firstOrNull { it.key == e.field } ?: section.fields.firstOrNull { it.key == e.field }
            PublishIssue(section, field, listField, e.item, ruleError(e.rule, field))
        }

    fun ruleError(rule: String?, field: FieldSpec?): ValidationError = when (rule) {
        "required" -> ValidationError.Required
        "maxLength" -> field?.maxLength?.let { ValidationError.MaxLength(it) } ?: ValidationError.Required
        "range" -> ValidationError.Range(field?.min, field?.max)
        "maxItems" -> field?.maxItems?.let { ValidationError.MaxItems(it) } ?: ValidationError.Option
        "option" -> ValidationError.Option
        "format" -> when (field?.type) {
            FieldType.Phone -> ValidationError.Phone
            FieldType.Email -> ValidationError.Email
            FieldType.Url -> ValidationError.Url
            else -> ValidationError.Option
        }
        "type" -> if (field?.type == FieldType.Number) ValidationError.NotANumber else ValidationError.Option
        else -> ValidationError.Option
    }
}
