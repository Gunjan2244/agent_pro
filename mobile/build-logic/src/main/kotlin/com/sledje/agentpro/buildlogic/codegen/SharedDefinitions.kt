package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.security.MessageDigest

/**
 * The parsed contents of `shared/` (docs/SHARED_DEFINITIONS.md). Plain data, no Gradle types,
 * so every generator and the verifier can be unit-tested with fixture directories.
 */
class SharedDefinitions(
    val catalog: JsonObject,
    val siteSchema: JsonObject,
    val designTokens: JsonObject,
    val templates: JsonObject,
    val i18nEn: Map<String, String>,
    val i18nHi: Map<String, String>,
    /** Raw text of site-schema.json, embedded verbatim in the catalog_sync migration. */
    val siteSchemaText: String,
) {
    /** Catalog lists of entries (arrays of objects with a `key`), in file order. */
    val catalogLists: List<CatalogList> by lazy {
        catalog.entries.mapNotNull { (name, value) ->
            val array = value as? JsonArray ?: return@mapNotNull null
            if (array.isEmpty() || array.any { it !is JsonObject }) return@mapNotNull null
            CatalogList(name, array.map { it.jsonObject })
        }
    }

    fun catalogList(name: String): CatalogList? = catalogLists.firstOrNull { it.jsonName == name }

    /** Template keys from templates/index.json, in file order. */
    val templateKeys: List<String> by lazy {
        (templates["templates"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("key") }
    }

    /**
     * sha256 over canonical (key-sorted, compact) catalog.json + site-schema.json + templates/index.json:
     * every file whose contents end up in the catalog_sync migration. When the catalog has
     * `privacyNotices`, the current notice hashes (which come from shared/i18n) are appended too, so a
     * notice text change produces a new catalog_sync. Without notices the hash is unchanged from before.
     */
    val catalogHash: String by lazy {
        var canonical = canonicalJson(catalog) + "\n" + canonicalJson(siteSchema) + "\n" + canonicalJson(templates)
        val notices = PrivacyNotices(this)
        if (notices.present) {
            val hashes = JsonObject(notices.current.mapValues { (_, versions) ->
                JsonObject(versions.entries.associate { (v, h) -> v.toString() to JsonObject(h.mapValues { JsonPrimitive(it.value) }) })
            })
            canonical += "\n" + canonicalJson(hashes)
        }
        MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** The i18n map of a catalog locale (en, hi), or null for one without an i18n file. */
    fun i18n(locale: String): Map<String, String>? = when (locale) {
        "en" -> i18nEn
        "hi" -> i18nHi
        else -> null
    }

    companion object {
        const val CATALOG = "catalog.json"
        const val SITE_SCHEMA = "site-schema.json"
        const val DESIGN_TOKENS = "design-tokens.json"
        const val TEMPLATES = "templates/index.json"
        const val I18N_EN = "i18n/en.json"
        const val I18N_HI = "i18n/hi.json"
        val FILES = listOf(CATALOG, SITE_SCHEMA, DESIGN_TOKENS, TEMPLATES, I18N_EN, I18N_HI)

        private val json = Json { isLenient = false }

        /** Loads all six files; throws [SharedDefinitionsException] naming every missing or invalid file. */
        fun load(sharedDir: File): SharedDefinitions {
            val problems = mutableListOf<String>()
            val parsed = FILES.associateWith { rel ->
                val f = File(sharedDir, rel)
                if (!f.isFile) {
                    problems += "shared/$rel is missing"; null
                } else try {
                    json.parseToJsonElement(f.readText()) as? JsonObject
                        ?: run { problems += "shared/$rel must be a JSON object"; null }
                } catch (e: Exception) {
                    problems += "shared/$rel is not valid JSON: ${e.message?.lineSequence()?.first()}"; null
                }
            }
            if (problems.isNotEmpty()) throw SharedDefinitionsException(problems)
            fun strings(rel: String): Map<String, String> {
                val obj = parsed.getValue(rel)!!
                return obj.mapValues { (k, v) ->
                    (v as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: run { problems += "shared/$rel: value of \"$k\" must be a string"; "" }
                }
            }
            val en = strings(I18N_EN)
            val hi = strings(I18N_HI)
            if (problems.isNotEmpty()) throw SharedDefinitionsException(problems)
            return SharedDefinitions(
                catalog = parsed.getValue(CATALOG)!!,
                siteSchema = parsed.getValue(SITE_SCHEMA)!!,
                designTokens = parsed.getValue(DESIGN_TOKENS)!!,
                templates = parsed.getValue(TEMPLATES)!!,
                i18nEn = en,
                i18nHi = hi,
                siteSchemaText = File(sharedDir, SITE_SCHEMA).readText(),
            )
        }

        fun canonicalJson(element: JsonElement): String = when (element) {
            is JsonObject -> element.entries.sortedBy { it.key }
                .joinToString(",", "{", "}") { (k, v) -> JsonPrimitive(k).toString() + ":" + canonicalJson(v) }
            is JsonArray -> element.joinToString(",", "[", "]") { canonicalJson(it) }
            else -> element.toString()
        }
    }
}

class SharedDefinitionsException(val problems: List<String>) :
    RuntimeException("Invalid shared/ definitions:\n" + problems.joinToString("\n") { "  - $it" })

/** One catalog list, e.g. `enquiryStatuses`. */
class CatalogList(val jsonName: String, val entries: List<JsonObject>) {
    val kotlinName: String get() = Naming.catalogTypeName(jsonName)
    val keys: List<String> get() = entries.mapNotNull { it.string("key") }
    val activeKeys: List<String> get() = entries.filterNot { it.bool("deprecated") == true }.mapNotNull { it.string("key") }
    val hasIcon: Boolean get() = entries.all { it.string("icon") != null }
    val hasTone: Boolean get() = entries.all { it.string("tone") != null }
}

// ---- JsonObject helpers -------------------------------------------------------------------------

fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

fun JsonObject.double(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toDoubleOrNull()

fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

fun JsonElement.isNull(): Boolean = this is JsonNull

fun JsonElement.primitiveString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonPrimitive.isIntegral(): Boolean = !isString && longOrNull != null

internal fun JsonPrimitive.isBooleanLiteral(): Boolean = !isString && (content == "true" || content == "false")

internal fun JsonElement.asPrimitiveOrNull(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

internal val JsonElement.prim: JsonPrimitive get() = jsonPrimitive
