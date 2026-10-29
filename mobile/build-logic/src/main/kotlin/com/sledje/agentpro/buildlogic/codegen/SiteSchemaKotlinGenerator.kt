package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generates `SiteSchema.kt` (package com.sledje.agentpro.core.catalog): the website sections as
 * `SectionSpec`s holding `FieldSpec`s (hand-written model in core/forms). `optionsFrom: "catalog.<list>"`
 * is resolved to that list's active entries, so `FormRenderer` never needs a catalog lookup. A list with an
 * `agentAllowed` field (offerCtaKinds) is narrowed to the entries agents may use, since every site field is the agent's.
 */
class SiteSchemaKotlinGenerator(private val defs: SharedDefinitions) {

    fun generate(): String {
        val w = CodeWriter()
        w.raw(Naming.GENERATED_HEADER)
        w.line("@file:Suppress(\"unused\")")
        w.line()
        w.line("package ${Naming.CATALOG_PACKAGE}")
        w.line()
        w.line("import ${Naming.RES_PACKAGE}.*")
        w.line("import ${Naming.FORMS_PACKAGE}.FieldDefault")
        w.line("import ${Naming.FORMS_PACKAGE}.FieldOption")
        w.line("import ${Naming.FORMS_PACKAGE}.FieldSpec")
        w.line("import ${Naming.FORMS_PACKAGE}.FieldType")
        w.line("import ${Naming.FORMS_PACKAGE}.ImageShape")
        w.line("import ${Naming.FORMS_PACKAGE}.SectionSpec")
        w.line()
        w.line("/** `shared/site-schema.json`: website sections in their default order. */")
        w.line("object SiteSchema {")
        w.indent {
            line("const val VERSION: Int = ${defs.siteSchema.long("version") ?: 0}")
            line()
            line("val sections: List<SectionSpec> = listOf(")
            indent {
                defs.siteSchema.array("sections").orEmpty().forEach { s -> writeSection(this, s as JsonObject) }
            }
            line(")")
            line()
            line("fun section(key: String): SectionSpec? = sections.firstOrNull { it.key == key }")
        }
        w.line("}")
        return w.toString()
    }

    private fun writeSection(w: CodeWriter, s: JsonObject) {
        val key = s.string("key")!!
        w.line("SectionSpec(")
        w.indent {
            line("key = ${Naming.kotlinString(key)},")
            line("label = ${label(s.string("labelKey"), "section $key")},")
            line("icon = Res.drawable.${Naming.drawableName(s.string("icon") ?: error("section $key has no icon"))},")
            if (s.bool("pinned") == true) line("pinned = true,")
            line("defaultVisible = ${s.bool("defaultVisible") ?: true},")
            line("fields = listOf(")
            indent { s.array("fields").orEmpty().forEach { writeField(this, it as JsonObject, key) } }
            line("),")
        }
        w.line("),")
    }

    private fun writeField(w: CodeWriter, f: JsonObject, path: String) {
        val key = f.string("key") ?: error("$path: field without key")
        val where = "$path.$key"
        val type = f.string("type") ?: error("$where: field without type")
        val typeConst = FIELD_TYPES[type] ?: error("$where: unknown field type \"$type\"")
        w.line("FieldSpec(")
        w.indent {
            line("key = ${Naming.kotlinString(key)},")
            line("type = FieldType.$typeConst,")
            line("label = ${label(f.string("labelKey"), where)},")
            if (f.bool("required") == true) line("required = true,")
            f.long("maxLength")?.let { line("maxLength = $it,") }
            integral(f, "min", where)?.let { line("min = ${it}L,") }
            integral(f, "max", where)?.let { line("max = ${it}L,") }
            f.long("maxItems")?.let { line("maxItems = $it,") }
            f.array("options")?.let { options ->
                line("options = listOf(")
                indent {
                    options.forEach { o ->
                        o as JsonObject
                        val icon = o.string("icon")?.let { ", icon = Res.drawable.${Naming.drawableName(it)}" } ?: ""
                        line("FieldOption(${Naming.kotlinString(o.string("key")!!)}, ${label(o.string("labelKey"), "$where option")}$icon),")
                    }
                }
                line("),")
            }
            f.string("optionsFrom")?.let { from ->
                val listName = from.removePrefix("catalog.")
                val list = defs.catalogList(listName)
                require(from.startsWith("catalog.") && list != null) { "$where: optionsFrom \"$from\" is not a catalog list" }
                val icon = if (list.hasIcon) ", it.icon" else ""
                val agentOnly = if (list.entries.any { it.containsKey(AGENT_ALLOWED) }) ".filter { it.${AGENT_ALLOWED} }" else ""
                line("options = ${list.kotlinName}.active$agentOnly.map { FieldOption(it.key, it.label$icon) },")
                line("optionsFrom = ${Naming.kotlinString(from)},")
            }
            f["default"]?.let { d ->
                if (d !is JsonNull) {
                    val p = d as? JsonPrimitive ?: error("$where: default must be a string, number or boolean")
                    val lit = when {
                        p.isString -> "FieldDefault.Text(${Naming.kotlinString(p.content)})"
                        p.isBooleanLiteral() -> "FieldDefault.Bool(${p.content})"
                        else -> "FieldDefault.Number(${p.content.toDouble()})"
                    }
                    line("default = $lit,")
                }
            }
            f.string("defaultKey")?.let { line("defaultText = Res.string.${Naming.stringResourceName(it)},") }
            f.string("aspect")?.let { line("aspect = ${Naming.kotlinString(it)},") }
            f.array("shownAs")?.let { shapes ->
                line("shownAs = listOf(")
                indent {
                    shapes.forEach { o ->
                        o as JsonObject
                        val round = if (o.bool("round") == true) ", round = true" else ""
                        val focusY = o.long("focusY")?.let { ", focusY = $it" } ?: ""
                        line("ImageShape(${Naming.kotlinString(o.string("aspect") ?: error("$where: shownAs entry without aspect"))}, ${label(o.string("labelKey"), "$where shownAs")}$round$focusY),")
                    }
                }
                line("),")
            }
            f.string("generated")?.let { line("generated = ${Naming.kotlinString(it)},") }
            if (f.bool("equals") == true) line("mustBeTrue = true,")
            f.long("maxDaysAhead")?.let { line("maxDaysAhead = $it,") }
            f.array("itemFields")?.let { items ->
                line("itemFields = listOf(")
                indent { items.forEach { writeField(this, it as JsonObject, where) } }
                line("),")
            }
        }
        w.line("),")
    }

    private fun integral(f: JsonObject, name: String, where: String): Long? {
        val v = f[name] as? JsonPrimitive ?: return null
        return v.content.toLongOrNull() ?: error("$where.$name must be a whole number")
    }

    private fun label(labelKey: String?, where: String): String =
        "Res.string.${Naming.stringResourceName(labelKey ?: error("$where has no labelKey"))}"

    companion object {
        /** JSON field type -> `FieldType` constant (core/forms/FieldSpec.kt). */
        val FIELD_TYPES = linkedMapOf(
            "text" to "Text", "multiline" to "Multiline", "number" to "Number", "phone" to "Phone",
            "email" to "Email", "url" to "Url", "image" to "Image", "select" to "Select",
            "multiSelect" to "MultiSelect", "toggle" to "Toggle", "swatch" to "Swatch", "date" to "Date",
            "list" to "ItemList", "offerRef" to "OfferRef", "offerList" to "OfferList",
        )

        /** Extra field of a catalog list marking the entries agents may pick (docs/OFFERS.md, offerCtaKinds). */
        const val AGENT_ALLOWED = "agentAllowed"
    }
}

/** Generates `AppConfig.kt` from mobile/local.properties. Missing keys become "" (never fails, never logs values). */
class AppConfigKotlinGenerator(private val properties: Map<String, String>) {
    fun generate(): String {
        val w = CodeWriter()
        w.raw("// GENERATED by mobile/build-logic from mobile/local.properties. Do not edit; never commit values.\n")
        w.line()
        w.line("package ${Naming.CONFIG_PACKAGE}")
        w.line()
        w.line("/** Build-time configuration (BuildConfig is Android-only). Empty string = not configured. */")
        w.line("object AppConfig {")
        w.indent {
            KEYS.forEach { k -> line("const val $k: String = ${Naming.kotlinString(properties[k].orEmpty().trim())}") }
        }
        w.line("}")
        return w.toString()
    }

    companion object {
        val KEYS = listOf("SUPABASE_URL", "SUPABASE_ANON_KEY", "SITE_BASE_URL", "GOOGLE_WEB_CLIENT_ID")
    }
}

/** Generates `values/strings_catalog.xml` and `values-hi/strings_catalog.xml` in Compose resources format. */
object StringsXmlGenerator {
    fun generate(strings: Map<String, String>, locale: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<!-- GENERATED by mobile/build-logic (./gradlew generateShared) from shared/i18n/$locale.json. Do not edit. -->\n")
        append("<resources>\n")
        strings.toSortedMap().forEach { (key, value) ->
            append("    <string name=\"").append(Naming.stringResourceName(key)).append("\">")
            append(escape(value)).append("</string>\n")
        }
        append("</resources>\n")
    }

    /**
     * Compose resources keep apostrophes and quotes literally (no Android-style `\'`), and only
     * unescape `\\`, `\n`, `\t` and `\uXXXX`. So: XML-escape `& < >`, double backslashes, and
     * encode newlines/tabs as `\n`/`\t`.
     */
    fun escape(value: String): String = buildString {
        for (c in value) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            '\r' -> {}
            else -> append(c)
        }
    }
}
