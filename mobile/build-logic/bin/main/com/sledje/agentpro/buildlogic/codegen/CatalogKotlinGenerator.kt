package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generates `Catalog.kt` (package com.sledje.agentpro.core.catalog) from catalog.json:
 * - `Tone` (from design-tokens.json `tone` keys) and the `CatalogEntry` interfaces;
 * - one enum per catalog list (EnquiryStatus, Interest, ..., AccentSwatch, FeatureId), each entry carrying
 *   `key`, `label: StringResource`, `icon: DrawableResource` and `tone` when the list has them, `order`,
 *   `deprecated`, plus the list's extra fields (terminal, months, minDays/maxDays, hex, default);
 * - `PlanSuggestions` (string arrays) and `Pricing`/`Retention` (objects), and `Catalog.VERSION`.
 * `privacyNotices` is generated separately ([PrivacyNoticesKotlinGenerator], PrivacyNotices.kt).
 */
class CatalogKotlinGenerator(private val defs: SharedDefinitions) {

    fun generate(): String {
        val w = CodeWriter()
        w.raw(Naming.GENERATED_HEADER)
        w.line("@file:Suppress(\"unused\", \"RedundantVisibilityModifier\", \"SpellCheckingInspection\")")
        w.line()
        w.line("package ${Naming.CATALOG_PACKAGE}")
        w.line()
        w.line("import ${Naming.RES_PACKAGE}.*")
        w.line("import org.jetbrains.compose.resources.DrawableResource")
        w.line("import org.jetbrains.compose.resources.StringResource")
        w.line()
        writeTone(w)
        writeInterfaces(w)
        w.line("object Catalog {")
        w.indent { line("const val VERSION: Int = ${defs.catalog.long("version") ?: 0}") }
        w.line("}")
        w.line()
        // privacyNotices has its own file (PrivacyNotices.kt: versions, text lists and hashes).
        for (list in defs.catalogLists) if (list.jsonName != PrivacyNoticeHashing.LIST) writeList(w, list)
        for ((name, value) in defs.catalog) {
            if (name == "version" || defs.catalogList(name) != null) continue
            when (value) {
                is JsonArray -> writeStringArrayObject(w, name, value)
                is JsonObject -> {
                    w.line("/** `catalog.$name`. */")
                    writeObject(w, Naming.objectName(name), value)
                    w.line()
                }
                else -> throw IllegalArgumentException("catalog.json: unsupported top-level field \"$name\"")
            }
        }
        return w.toString()
    }

    private fun writeTone(w: CodeWriter) {
        val tones = defs.designTokens.obj("tone")?.keys?.toList().orEmpty()
        w.line("/** Status tones (design-tokens.json `tone`). Colours: `Tokens.Tones`. */")
        w.line("enum class Tone(val key: String) {")
        w.indent {
            tones.forEach { line("${Naming.enumConstant(it)}(${Naming.kotlinString(it)}),") }
            line(";")
            line("companion object {")
            indent { line("fun fromKey(key: String): Tone? = entries.firstOrNull { it.key == key }") }
            line("}")
        }
        w.line("}")
        w.line()
    }

    private fun writeInterfaces(w: CodeWriter) {
        w.line("/** Common shape of every catalog entry. `key` is the stable value stored in the database. */")
        w.line("interface CatalogEntry {")
        w.indent {
            line("val key: String")
            line("val label: StringResource")
            line("val order: Int")
            line("/** Removed from the product but kept so stored values still parse (never offered for new data). */")
            line("val deprecated: Boolean")
        }
        w.line("}")
        w.line()
        w.line("interface CatalogIconEntry : CatalogEntry {")
        w.indent { line("val icon: DrawableResource") }
        w.line("}")
        w.line()
        w.line("interface CatalogTonedEntry : CatalogIconEntry {")
        w.indent { line("val tone: Tone") }
        w.line("}")
        w.line()
    }

    private fun writeList(w: CodeWriter, list: CatalogList) {
        val type = list.kotlinName
        val extras = extraFields(list)
        val iface = when {
            list.hasTone && list.hasIcon -> "CatalogTonedEntry"
            list.hasIcon -> "CatalogIconEntry"
            else -> "CatalogEntry"
        }
        w.line("/** `catalog.${list.jsonName}`. */")
        w.line("enum class $type(")
        w.indent {
            line("override val key: String,")
            line("override val label: StringResource,")
            if (list.hasIcon) line("override val icon: DrawableResource,")
            if (list.hasTone) line("override val tone: Tone,")
            line("override val order: Int,")
            extras.forEach { line("val ${it.property}: ${it.type},") }
            line("override val deprecated: Boolean = false,")
        }
        w.line(") : $iface {")
        w.indent {
            for (entry in list.entries) {
                val key = entry.string("key")!!
                val args = mutableListOf<String>()
                args += Naming.kotlinString(key)
                args += "Res.string.${Naming.stringResourceName(entry.string("labelKey")!!)}"
                if (list.hasIcon) args += "Res.drawable.${Naming.drawableName(entry.string("icon")!!)}"
                if (list.hasTone) args += "Tone.${Naming.enumConstant(entry.string("tone")!!)}"
                args += (entry.long("order") ?: 0).toString()
                extras.forEach { args += "${it.property} = ${literal(entry[it.json], it)}" }
                if (entry.bool("deprecated") == true) args += "deprecated = true"
                line("${Naming.enumConstant(key)}(${args.joinToString(", ")}),")
            }
            line(";")
            line()
            line("companion object {")
            indent {
                line("fun fromKey(key: String): $type? = entries.firstOrNull { it.key == key }")
                line()
                line("/** Entries offered for new data (deprecated ones excluded), in display order. */")
                line("val active: List<$type> get() = entries.filterNot { it.deprecated }.sortedBy { it.order }")
            }
            line("}")
        }
        w.line("}")
        w.line()
    }

    private class Extra(val json: String, val property: String, val type: String, val nullable: Boolean, val kind: Kind)
    private enum class Kind { BOOLEAN, INT, LONG, DOUBLE, STRING }

    private fun extraFields(list: CatalogList): List<Extra> {
        val names = LinkedHashSet<String>()
        list.entries.forEach { e -> e.keys.filterTo(names) { it !in STANDARD_FIELDS } }
        return names.map { name ->
            val values = list.entries.map { it[name] }
            val nullable = values.any { it == null || it is JsonNull }
            val present = values.filterNotNull().filter { it !is JsonNull }
            require(present.all { it is JsonPrimitive }) {
                "catalog.${list.jsonName}.$name: only string, number, boolean or null values are supported"
            }
            val prims = present.map { it as JsonPrimitive }
            val kind = when {
                prims.isEmpty() -> Kind.STRING
                prims.all { it.isBooleanLiteral() } -> Kind.BOOLEAN
                prims.all { it.isIntegral() } ->
                    if (prims.all { it.content.toLong() in Int.MIN_VALUE..Int.MAX_VALUE }) Kind.INT else Kind.LONG
                prims.all { !it.isString && it.content.toDoubleOrNull() != null } -> Kind.DOUBLE
                prims.all { it.isString } -> Kind.STRING
                else -> throw IllegalArgumentException("catalog.${list.jsonName}.$name: mixed value types")
            }
            val base = when (kind) {
                Kind.BOOLEAN -> "Boolean"; Kind.INT -> "Int"; Kind.LONG -> "Long"
                Kind.DOUBLE -> "Double"; Kind.STRING -> "String"
            }
            val property = Naming.propertyName(name)
            require(property !in RESERVED) { "catalog.${list.jsonName}.$name: \"$name\" can't be used as a field name" }
            Extra(name, property, if (nullable) "$base?" else base, nullable, kind)
        }
    }

    private fun literal(value: JsonElement?, extra: Extra): String {
        if (value == null || value is JsonNull) return "null"
        val p = value as JsonPrimitive
        return when (extra.kind) {
            Kind.STRING -> Naming.kotlinString(p.content)
            Kind.LONG -> p.content + "L"
            Kind.DOUBLE -> p.content.toDouble().toString()
            else -> p.content
        }
    }

    private fun writeStringArrayObject(w: CodeWriter, name: String, array: JsonArray) {
        require(array.all { it is JsonPrimitive && it.isString }) { "catalog.$name: only arrays of strings are supported" }
        w.line("/** `catalog.$name`. */")
        w.line("object ${Naming.objectName(name)} {")
        w.indent {
            line("val all: List<String> = listOf(")
            indent { array.forEach { line(Naming.kotlinString(it.primitiveString()!!) + ",") } }
            line(")")
        }
        w.line("}")
        w.line()
    }

    companion object {
        private val STANDARD_FIELDS = setOf("key", "labelKey", "icon", "tone", "order", "deprecated")
        private val RESERVED = setOf("name", "ordinal", "entries", "key", "label", "icon", "tone", "order", "deprecated")

        /** Writes a JSON object as nested Kotlin objects with `const val`s and `listOf(...)`s. */
        fun writeObject(w: CodeWriter, name: String, obj: JsonObject) {
            w.line("object $name {")
            w.indent {
                for ((k, v) in obj) {
                    val prop = Naming.propertyName(k)
                    when (v) {
                        is JsonObject -> writeObject(this, Naming.objectName(k), v)
                        is JsonArray -> line("val $prop: ${listType(k, v)} = listOf(${v.joinToString(", ") { primitiveLiteral(k, it) }})")
                        is JsonNull -> line("val $prop: String? = null")
                        is JsonPrimitive -> line("const val $prop: ${primitiveType(v)} = ${primitiveLiteral(k, v)}")
                    }
                }
            }
            w.line("}")
        }

        private fun primitiveType(p: JsonPrimitive): String = when {
            p.isString -> "String"
            p.isBooleanLiteral() -> "Boolean"
            p.isIntegral() -> if (p.content.toLong() in Int.MIN_VALUE..Int.MAX_VALUE) "Int" else "Long"
            else -> "Double"
        }

        private fun listType(name: String, array: JsonArray): String {
            if (array.isEmpty()) return "List<String>"
            val types = array.map {
                require(it is JsonPrimitive && it !is JsonNull) { "\"$name\": only arrays of primitives are supported" }
                primitiveType(it)
            }.toSet()
            require(types.size == 1) { "\"$name\": mixed array value types" }
            return "List<${types.single()}>"
        }

        private fun primitiveLiteral(name: String, e: JsonElement): String {
            require(e is JsonPrimitive && e !is JsonNull) { "\"$name\": unsupported value" }
            return when (primitiveType(e)) {
                "String" -> Naming.kotlinString(e.content)
                "Long" -> e.content + "L"
                "Double" -> e.content.toDouble().toString()
                else -> e.content
            }
        }
    }
}

/** Minimal indenting writer for generated Kotlin. */
class CodeWriter {
    private val sb = StringBuilder()
    private var level = 0

    fun raw(text: String) { sb.append(text) }

    fun line(text: String = "") {
        if (text.isEmpty()) sb.append('\n') else sb.append("    ".repeat(level)).append(text).append('\n')
    }

    fun indent(block: CodeWriter.() -> Unit) {
        level++
        block()
        level--
    }

    override fun toString(): String = sb.toString()
}
