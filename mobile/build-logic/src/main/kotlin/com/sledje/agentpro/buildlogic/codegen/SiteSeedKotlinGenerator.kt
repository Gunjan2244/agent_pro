package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.util.Locale

/**
 * One field's value form in `shared/site-seed.json` (docs/SHARED_DEFINITIONS.md, site-seed.json). Exactly one of
 * `variants`, `fromProfile`, `value`, `byLocale` or `items` per field.
 */
sealed interface SeedForm {
    /** Text: i18n keys, one picked per site; [Variant.fallback] replaces a text whose optional placeholder is blank. */
    data class Variants(val variants: List<Variant>) : SeedForm
    data class FromProfile(val field: String) : SeedForm
    data class Literal(val value: JsonElement) : SeedForm
    /** A value per site locale (catalog locale key -> JSON value). */
    data class ByLocale(val values: Map<String, JsonElement>) : SeedForm
    /** A list field: one map of item field -> form per item. */
    data class Items(val items: List<Map<String, SeedForm>>) : SeedForm
    data class Invalid(val reason: String) : SeedForm

    data class Variant(val key: String, val fallback: String?)
}

/** Reading `shared/site-seed.json`. Shared by [SiteSeedKotlinGenerator] and the verifier. */
object SiteSeedJson {
    val FORMS = listOf("variants", "fromProfile", "value", "byLocale", "items")

    /** Profile values a seed may read (`fromProfile`, `placeholders`): the app's Profile, plus the derived first name. */
    val PROFILE_FIELDS = listOf("fullName", "firstName", "phone", "email", "designation", "city")

    fun form(element: JsonElement, allowItems: Boolean = true): SeedForm {
        val obj = element as? JsonObject ?: return SeedForm.Invalid("must be an object with one of ${FORMS.joinToString()}")
        val present = FORMS.filter { obj.containsKey(it) }
        val extra = obj.keys - FORMS.toSet()
        if (present.size != 1 || extra.isNotEmpty()) {
            return SeedForm.Invalid("must have exactly one of ${FORMS.joinToString()}" + if (extra.isNotEmpty()) " (unknown $extra)" else "")
        }
        return when (present.single()) {
            "variants" -> {
                val list = obj["variants"] as? JsonArray
                if (list.isNullOrEmpty()) return SeedForm.Invalid("\"variants\" must be a non-empty array")
                val variants = list.map { v ->
                    when {
                        v is JsonPrimitive && v.isString -> SeedForm.Variant(v.content, null)
                        v is JsonObject && v.keys.all { it == "key" || it == "fallback" } && v.string("key") != null &&
                            (v["fallback"] == null || v.string("fallback") != null) -> SeedForm.Variant(v.string("key")!!, v.string("fallback"))
                        else -> return SeedForm.Invalid("each variant must be an i18n key or {\"key\", \"fallback\"}")
                    }
                }
                SeedForm.Variants(variants)
            }
            "fromProfile" -> obj.string("fromProfile")?.let { SeedForm.FromProfile(it) }
                ?: SeedForm.Invalid("\"fromProfile\" must be a string")
            "value" -> obj["value"]!!.let { if (it is JsonNull) SeedForm.Invalid("\"value\" can't be null") else SeedForm.Literal(it) }
            "byLocale" -> (obj["byLocale"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let { SeedForm.ByLocale(it) }
                ?: SeedForm.Invalid("\"byLocale\" must be a non-empty object of locale -> value")
            else -> {
                if (!allowItems) return SeedForm.Invalid("\"items\" can't be nested inside a list item")
                val items = obj["items"] as? JsonArray ?: return SeedForm.Invalid("\"items\" must be an array of objects")
                SeedForm.Items(items.map { item ->
                    val o = item as? JsonObject ?: return SeedForm.Invalid("\"items\" must be an array of objects")
                    o.mapValues { (_, v) -> form(v, allowItems = false) }
                })
            }
        }
    }

    /** `sections` as section key -> field key -> raw form JSON, in file order. */
    fun sections(seed: JsonObject): Map<String, Map<String, JsonElement>> =
        seed.obj("sections").orEmpty().mapValues { (_, s) -> (s as? JsonObject).orEmpty() }

    fun placeholders(seed: JsonObject): Map<String, String> =
        seed.obj("placeholders").orEmpty().mapValues { it.value.primitiveString().orEmpty() }

    fun requiredPlaceholders(seed: JsonObject): List<String> =
        seed.array("requiredPlaceholders").orEmpty().mapNotNull { it.primitiveString() }

    fun hidden(seed: JsonObject): List<String> =
        seed.obj("visibility")?.array("hidden").orEmpty().mapNotNull { it.primitiveString() }

    fun visibleOnlyInFamily(seed: JsonObject): Map<String, String> =
        seed.obj("visibility")?.obj("visibleOnlyInFamily").orEmpty().mapValues { it.value.primitiveString().orEmpty() }

    /** Every i18n key the seed uses (variant keys and fallbacks), in file order. */
    fun textKeys(seed: JsonObject): List<String> {
        val keys = LinkedHashSet<String>()
        fun walk(f: SeedForm) {
            when (f) {
                is SeedForm.Variants -> f.variants.forEach { keys += it.key; it.fallback?.let { fb -> keys += fb } }
                is SeedForm.Items -> f.items.forEach { item -> item.values.forEach(::walk) }
                else -> {}
            }
        }
        sections(seed).values.forEach { fields -> fields.values.forEach { walk(form(it)) } }
        return keys.toList()
    }

    val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}")

    /**
     * Reference implementation of the variant pick that the generated `SiteSeed.pick` (and any JS port) must match:
     * `fnv1a32(utf8(seed + ":" + salt)) mod count`, FNV-1a 32-bit (offset 2166136261, prime 16777619).
     */
    fun fnv1a32(value: String): Long {
        var hash = 2166136261L
        for (b in value.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash = (hash * 16777619L) and 0xFFFFFFFFL
        }
        return hash
    }

    fun pick(seed: String, salt: String, count: Int): Int = (fnv1a32("$seed:$salt") % count).toInt()
}

/**
 * Public site text rules (DECISIONS #77): `catalog.publicTextRules.blockedTerms` and the reference matcher.
 * A term matches when it occurs in the text **word-bounded and case-insensitively**: both are NFC-normalized and
 * lower-cased (Locale.ROOT); the term's words may be separated by any whitespace in the text; the characters just
 * before and after the match must not be a letter, combining mark or digit (`\p{L}\p{M}\p{N}`, so Devanagari vowel
 * signs count as part of a word). The server check (WP2) and the web tests follow the same rule.
 */
object PublicText {
    fun blockedTerms(defs: SharedDefinitions): List<String> =
        defs.catalog.obj("publicTextRules")?.array("blockedTerms").orEmpty().mapNotNull { it.primitiveString() }

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun pattern(term: String): Regex {
        val words = normalize(term).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return Regex("(?<![\\p{L}\\p{M}\\p{N}])" + words.joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}\\p{M}\\p{N}])")
    }

    /** The blocked terms found in [text], in list order. */
    fun violations(text: String, terms: List<String>): List<String> {
        val t = normalize(text)
        return terms.filter { it.isNotBlank() && pattern(it).containsMatchIn(t) }
    }

    /**
     * Single everyday words that must never be blocked on their own (they're generic Hindi/English); a blocked
     * term containing them must have more words (e.g. "jeevan anand").
     */
    val GENERIC_SINGLE_WORDS = setOf(
        "jeevan", "bima", "जीवन", "बीमा", "life", "health", "insurance", "plan", "policy", "term", "care", "max",
        "star", "digit", "new", "plus", "smart", "saral", "सरल", "dhan", "धन",
    )
}

/**
 * Generates `SiteSeed.kt` (package com.sledje.agentpro.core.catalog) from `shared/site-seed.json` + `shared/i18n`:
 * the starter content of a new site with every text resolved for **every** catalog locale (the site's language can
 * differ from the app's), plus the pure helpers StarterContent uses: [fnv1a32]-based variant picking and placeholder
 * filling with fallbacks. Data and tiny pure functions only.
 */
class SiteSeedKotlinGenerator(private val defs: SharedDefinitions) {

    fun generate(): String {
        val seed = defs.siteSeed ?: error("shared/site-seed.json is missing")
        val locales = activeLocales()
        val textKeys = SiteSeedJson.textKeys(seed)
        val w = CodeWriter()
        w.raw(Naming.GENERATED_HEADER)
        w.line("@file:Suppress(\"unused\", \"SpellCheckingInspection\")")
        w.line()
        w.line("package ${Naming.CATALOG_PACKAGE}")
        w.line()
        w.line("import kotlinx.serialization.json.JsonArray")
        w.line("import kotlinx.serialization.json.JsonElement")
        w.line("import kotlinx.serialization.json.JsonObject")
        w.line("import kotlinx.serialization.json.JsonPrimitive")
        w.line()
        w.raw(MODEL)
        w.line()
        w.line("/** `shared/site-seed.json`: the starter content of a new site (DECISIONS #78; docs/SHARED_DEFINITIONS.md). */")
        w.line("object SiteSeed {")
        w.indent {
            line("const val VERSION: Int = ${seed.long("version") ?: 0}")
            line("/** The locale whose text is used when a site locale has none (the first catalog locale). */")
            line("const val DEFAULT_LOCALE: String = ${Naming.kotlinString(locales.first())}")
            line("val LOCALES: List<String> = listOf(${locales.joinToString { Naming.kotlinString(it) }})")
            line("/** Placeholder -> profile value it is filled from (`firstName` is the first word of `fullName`). */")
            line("val PLACEHOLDERS: Map<String, String> = mapOf(${SiteSeedJson.placeholders(seed).entries.joinToString { (k, v) -> "${Naming.kotlinString(k)} to ${Naming.kotlinString(v)}" }})")
            line("/** Placeholders that are always filled; a fallback text uses only these. */")
            line("val REQUIRED_PLACEHOLDERS: List<String> = listOf(${SiteSeedJson.requiredPlaceholders(seed).joinToString { Naming.kotlinString(it) }})")
            line("/** Sections every new site starts hidden. */")
            line("val hidden: List<String> = listOf(${SiteSeedJson.hidden(seed).joinToString { Naming.kotlinString(it) }})")
            line("/** Section -> the template family in which it starts visible; hidden in every other family. */")
            line("val visibleOnlyInFamily: Map<String, String> = mapOf(${SiteSeedJson.visibleOnlyInFamily(seed).entries.joinToString { (k, v) -> "${Naming.kotlinString(k)} to ${Naming.kotlinString(v)}" }})")
            line()
            line("/** Every seed text by i18n key, resolved for every locale (placeholders unfilled). */")
            line("val texts: Map<String, SeedText> = mapOf(")
            indent {
                textKeys.forEach { key ->
                    val byLocale = locales.joinToString { l ->
                        val text = defs.i18n(l)?.get(key) ?: error("site-seed: i18n key \"$key\" is missing for locale $l")
                        "${Naming.kotlinString(l)} to ${Naming.kotlinString(text)}"
                    }
                    line("${Naming.kotlinString(key)} to SeedText(${Naming.kotlinString(key)}, mapOf($byLocale)),")
                }
            }
            line(")")
            line()
            line("/** The seeded sections in file order; `sectionOrder` comes from the chosen template (`TemplateSpec.sections`). */")
            line("val sections: List<SeedSection> = listOf(")
            indent {
                SiteSeedJson.sections(seed).forEach { (section, fields) ->
                    line("SeedSection(")
                    indent {
                        line("key = ${Naming.kotlinString(section)},")
                        line("fields = mapOf(")
                        indent { fields.forEach { (field, json) -> writeEntry(this, field, SiteSeedJson.form(json), "$section.$field") } }
                        line("),")
                    }
                    line("),")
                }
            }
            line(")")
            line()
            raw(HELPERS.lines().joinToString("\n", postfix = "\n") { if (it.isBlank()) "" else "    $it" })
        }
        w.line("}")
        return w.toString()
    }

    private fun writeEntry(w: CodeWriter, field: String, form: SeedForm, where: String) {
        when (form) {
            is SeedForm.Items -> {
                w.line("${Naming.kotlinString(field)} to SeedValue.Items(listOf(")
                w.indent {
                    form.items.forEachIndexed { i, item ->
                        line("mapOf(")
                        indent { item.forEach { (k, f) -> writeEntry(this, k, f, "$where[$i].$k") } }
                        line("),")
                    }
                }
                w.line(")),")
            }
            else -> w.line("${Naming.kotlinString(field)} to ${valueExpr(form, where)},")
        }
    }

    private fun valueExpr(form: SeedForm, where: String): String = when (form) {
        is SeedForm.Variants -> "SeedValue.Variants(listOf(" + form.variants.joinToString { v ->
            val fb = v.fallback?.let { ", texts.getValue(${Naming.kotlinString(it)})" } ?: ""
            "SeedVariant(texts.getValue(${Naming.kotlinString(v.key)})$fb)"
        } + "))"
        is SeedForm.FromProfile -> "SeedValue.FromProfile(${Naming.kotlinString(form.field)})"
        is SeedForm.Literal -> "SeedValue.Literal(${json(form.value)})"
        is SeedForm.ByLocale -> "SeedValue.ByLocale(mapOf(" +
            form.values.entries.joinToString { (l, v) -> "${Naming.kotlinString(l)} to ${json(v)}" } + "))"
        is SeedForm.Items -> error("site-seed $where: nested items")
        is SeedForm.Invalid -> error("site-seed $where: ${form.reason}")
    }

    /** A JSON value as a Kotlin expression building the same kotlinx.serialization JsonElement. */
    private fun json(e: JsonElement): String = when (e) {
        is JsonNull -> error("site-seed: null values aren't supported")
        is JsonPrimitive -> when {
            e.isString -> "JsonPrimitive(${Naming.kotlinString(e.content)})"
            e.content == "true" || e.content == "false" -> "JsonPrimitive(${e.content})"
            e.content.toLongOrNull() != null -> "JsonPrimitive(${e.content}L)"
            else -> "JsonPrimitive(${e.content.toDouble()})"
        }
        is JsonArray -> "JsonArray(listOf(${e.joinToString { json(it) }}))"
        is JsonObject -> "JsonObject(mapOf(${e.entries.joinToString { (k, v) -> "${Naming.kotlinString(k)} to ${json(v)}" }}))"
    }

    private fun activeLocales(): List<String> {
        val locales = defs.catalogList("locales") ?: error("catalog.json has no \"locales\" list")
        return locales.entries.filterNot { it.bool("deprecated") == true }.sortedBy { it.long("order") ?: 0 }
            .mapNotNull { it.string("key") }.also { require(it.isNotEmpty()) { "catalog.locales: no active locale" } }
    }

    companion object {
        private val MODEL = """
            |/** One seed text: an i18n key of shared/i18n with its text per locale (placeholders such as `{name}` unfilled). */
            |data class SeedText(val key: String, val byLocale: Map<String, String>) {
            |    /** The text in [locale], or in [SiteSeed.DEFAULT_LOCALE] when that locale has none. */
            |    fun raw(locale: String): String = byLocale[locale] ?: byLocale.getValue(SiteSeed.DEFAULT_LOCALE)
            |}
            |
            |/** One choice for a text field: [text], or [fallback] when a placeholder that [text] uses is blank. */
            |data class SeedVariant(val text: SeedText, val fallback: SeedText? = null)
            |
            |/** How a seeded field gets its value. */
            |sealed interface SeedValue {
            |    /** Text: one variant, picked per site with [SiteSeed.pick], then filled with [SiteSeed.fill]. */
            |    data class Variants(val variants: List<SeedVariant>) : SeedValue
            |    /** A profile value, one of [SiteSeed.PROFILE_FIELDS]; left out when blank. */
            |    data class FromProfile(val field: String) : SeedValue
            |    /** A fixed JSON value (a select key, a multiSelect array, ...). */
            |    data class Literal(val value: JsonElement) : SeedValue
            |    /** A JSON value chosen by the site's locale. */
            |    data class ByLocale(val values: Map<String, JsonElement>) : SeedValue {
            |        fun value(locale: String): JsonElement = values[locale] ?: values.getValue(SiteSeed.DEFAULT_LOCALE)
            |    }
            |    /** A list field: one map of item field -> value per item, in order. */
            |    data class Items(val items: List<Map<String, SeedValue>>) : SeedValue
            |}
            |
            |/** The seeded fields of one site-schema section. */
            |data class SeedSection(val key: String, val fields: Map<String, SeedValue>)
            |""".trimMargin()

        private val HELPERS = """
            |val PROFILE_FIELDS: List<String> = listOf(${SiteSeedJson.PROFILE_FIELDS.joinToString { Naming.kotlinString(it) }})
            |
            |fun section(key: String): SeedSection? = sections.firstOrNull { it.key == key }
            |
            |/** The text of [key] in [locale] (placeholders unfilled), or null for a key the seed doesn't use. */
            |fun text(locale: String, key: String): String? = texts[key]?.raw(locale)
            |
            |/** Sections a new site of template [family] (a `TemplateFamily` key) starts hidden. */
            |fun hiddenSections(family: String): List<String> =
            |    (hidden + visibleOnlyInFamily.filterValues { it != family }.keys).distinct()
            |
            |/** 32-bit FNV-1a over the UTF-8 bytes of [value]. */
            |fun fnv1a32(value: String): UInt {
            |    var hash = 2166136261u
            |    for (byte in value.encodeToByteArray()) {
            |        hash = hash xor (byte.toUInt() and 0xFFu)
            |        hash *= 16777619u
            |    }
            |    return hash
            |}
            |
            |/** The salt of a section field (`hero.subheadline`) or of a list item's field (`faq.items.2.answer`). */
            |fun salt(section: String, field: String, index: Int? = null, itemField: String? = null): String =
            |    if (index == null || itemField == null) "${'$'}section.${'$'}field" else "${'$'}section.${'$'}field.${'$'}index.${'$'}itemField"
            |
            |/** The variant index for [seed] (the agent's user id) and [salt]: fnv1a32(seed + ":" + salt) mod [count]. */
            |fun pick(seed: String, salt: String, count: Int): Int {
            |    require(count > 0) { "count must be > 0" }
            |    return (fnv1a32("${'$'}seed:${'$'}salt") % count.toUInt()).toInt()
            |}
            |
            |/** The placeholder names used in [text], e.g. {"name", "city"}. */
            |fun placeholdersIn(text: String): Set<String> =
            |    Regex("\\{([A-Za-z0-9_]+)\\}").findAll(text).map { it.groupValues[1] }.toSet()
            |
            |/** [text] with each `{placeholder}` replaced by its trimmed value in [values] (unknown ones become ""). */
            |fun fill(text: String, values: Map<String, String>): String =
            |    Regex("\\{([A-Za-z0-9_]+)\\}").replace(text) { values[it.groupValues[1]]?.trim().orEmpty() }
            |
            |/**
            | * The text of [variant] in [locale] with [values] filled in. When a placeholder the text uses is blank, its
            | * fallback is used instead; null when that is impossible too (no fallback, or a required placeholder is blank).
            | */
            |fun fill(variant: SeedVariant, locale: String, values: Map<String, String>): String? {
            |    fun complete(text: String) = placeholdersIn(text).all { !values[it].isNullOrBlank() }
            |    val main = variant.text.raw(locale)
            |    if (complete(main)) return fill(main, values)
            |    val fallback = variant.fallback?.raw(locale) ?: return null
            |    return if (complete(fallback)) fill(fallback, values) else null
            |}
            |
            |/** Picks a variant of [value] with [pick] and fills it with [fill]. */
            |fun resolve(value: SeedValue.Variants, seed: String, salt: String, locale: String, values: Map<String, String>): String? =
            |    fill(value.variants[pick(seed, salt, value.variants.size)], locale, values)
            |""".trimMargin()
    }
}
