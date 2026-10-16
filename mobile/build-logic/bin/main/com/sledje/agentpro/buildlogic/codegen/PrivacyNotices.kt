package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.text.Normalizer

/**
 * `catalog.privacyNotices` (docs/PRIVACY.md, docs/SHARED_DEFINITIONS.md "Privacy notices"):
 * versioned notices whose text lives in shared/i18n. Each (notice, version, locale) has a stable
 * sha256 over a canonical serialization of its text, so a consent row records exactly which text was shown.
 *
 * Canonicalization v1 (must never change; a new rule needs a new [CANONICALIZATION] number):
 * 1. Take the i18n values of `textKeys`, in the order listed, from the locale's file. Placeholders such as
 *    `{name}` are NOT substituted: the hash covers the template text, not one agent's rendering.
 * 2. In each value, turn `\r\n` and `\r` into `\n`, then normalize to Unicode NFC.
 * 3. Join the values with a single `\n`.
 * 4. Serialize `{"key":<notice key>,"locale":<locale>,"text":<joined text>,"version":<int>}` as compact JSON
 *    with keys in that (sorted) order, no whitespace, strings escaped as JSON does (`"` `\` and control
 *    characters only; other characters stay raw). This equals JavaScript's
 *    `JSON.stringify({key, locale, text, version})`.
 * 5. sha256 over the UTF-8 bytes, as 64 lowercase hex characters.
 */
object PrivacyNoticeHashing {
    const val LIST = "privacyNotices"
    const val CANONICALIZATION = 1

    fun canonicalText(values: List<String>): String =
        values.joinToString("\n") { Normalizer.normalize(it.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC) }

    fun canonicalPayload(key: String, version: Int, locale: String, values: List<String>): String =
        SharedDefinitions.canonicalJson(
            JsonObject(
                mapOf(
                    "key" to JsonPrimitive(key),
                    "locale" to JsonPrimitive(locale),
                    "text" to JsonPrimitive(canonicalText(values)),
                    "version" to JsonPrimitive(version),
                )
            )
        )

    fun hash(key: String, version: Int, locale: String, values: List<String>): String =
        sha256(canonicalPayload(key, version, locale, values))

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Upper-case `{TOKENS}` in i18n values are fill-before-release placeholders (e.g. `{GRIEVANCE_EMAIL}`). */
    val RELEASE_PLACEHOLDER = Regex("\\{[A-Z][A-Z0-9_]*}")
}

/** One `catalog.privacyNotices` entry. */
data class NoticeSpec(
    val key: String,
    val labelKey: String,
    val order: Int,
    val version: Int,
    val textKeys: List<String>,
    val deprecated: Boolean,
)

/**
 * Notices parsed from [defs] plus their hashes. Lenient: malformed entries are skipped here and
 * reported by the verifier (`checkPrivacy`), so `verifyShared` lists every problem instead of crashing.
 */
class PrivacyNotices(private val defs: SharedDefinitions) {

    val present: Boolean get() = defs.catalogList(PrivacyNoticeHashing.LIST) != null

    val notices: List<NoticeSpec> by lazy {
        defs.catalogList(PrivacyNoticeHashing.LIST)?.entries.orEmpty().mapNotNull { e ->
            val key = e.string("key") ?: return@mapNotNull null
            val version = e.long("version")?.toInt() ?: return@mapNotNull null
            val textKeys = e.array("textKeys")?.map { it.primitiveString() ?: return@mapNotNull null } ?: return@mapNotNull null
            NoticeSpec(key, e.string("labelKey").orEmpty(), (e.long("order") ?: 0).toInt(), version, textKeys, e.bool("deprecated") == true)
        }
    }

    /** Catalog locale keys that have an i18n file (en, hi), in catalog order. */
    val locales: List<String> by lazy {
        val keys = defs.catalogList("locales")?.entries.orEmpty().sortedBy { it.long("order") ?: 0 }.mapNotNull { it.string("key") }
        keys.filter { i18n(it) != null }
    }

    fun i18n(locale: String): Map<String, String>? = defs.i18n(locale)

    /** sha256 for [notice] in [locale], or null when a text key is missing from that locale. */
    fun hash(notice: NoticeSpec, locale: String): String? {
        val dict = i18n(locale) ?: return null
        val values = notice.textKeys.map { dict[it] ?: return null }
        return PrivacyNoticeHashing.hash(notice.key, notice.version, locale, values)
    }

    /** locale -> hash for the current version (locales with a missing text key are left out). */
    fun hashes(notice: NoticeSpec): Map<String, String> =
        locales.mapNotNull { l -> hash(notice, l)?.let { l to it } }.toMap()

    /** key -> (current version -> (locale -> hash)): the shape of the lockfile's `privacyNoticeHashes`. */
    val current: Map<String, Map<Int, Map<String, String>>> by lazy {
        notices.associate { n -> n.key to mapOf(n.version to hashes(n)) }
    }

    /**
     * Every released (key, version) -> locale -> hash: the lockfile's entries plus the current versions.
     * A current version that is already locked keeps its locked hashes (verifyShared fails if they differ).
     */
    fun released(lock: Map<String, Map<Int, Map<String, String>>>): Map<String, Map<Int, Map<String, String>>> {
        val result = LinkedHashMap<String, MutableMap<Int, Map<String, String>>>()
        lock.forEach { (k, versions) -> result.getOrPut(k) { sortedMapOf() }.putAll(versions) }
        current.forEach { (k, versions) ->
            val m = result.getOrPut(k) { sortedMapOf() }
            versions.forEach { (v, h) -> m.putIfAbsent(v, h) }
        }
        return result
    }

    /** Distinct release placeholders (`{GRIEVANCE_EMAIL}`, ...) still present in any i18n value, sorted. */
    val releaseBlockers: List<String> by lazy {
        (defs.i18nEn.values + defs.i18nHi.values)
            .flatMap { PrivacyNoticeHashing.RELEASE_PLACEHOLDER.findAll(it).map { m -> m.value }.toList() }
            .distinct().sorted()
    }

    /** `shared/generated/privacy-notices.json`, read by the web renderer (active notices only). */
    fun webJson(): String {
        val notices = this.notices.filterNot { it.deprecated }.sortedBy { it.order }.associate { n ->
            n.key to JsonObject(
                mapOf(
                    "version" to JsonPrimitive(n.version),
                    "labelKey" to JsonPrimitive(n.labelKey),
                    "textKeys" to JsonArray(n.textKeys.map { JsonPrimitive(it) }),
                    "hash" to JsonObject(hashes(n).mapValues { JsonPrimitive(it.value) }),
                )
            )
        }
        val root = JsonObject(
            mapOf(
                "comment" to JsonPrimitive(
                    "GENERATED by ./gradlew generateShared from shared/catalog.json (privacyNotices) and shared/i18n. " +
                        "Do not edit. Send {notice_key, version, hash} with a consent; the hash rule is in docs/SHARED_DEFINITIONS.md."
                ),
                "canonicalization" to JsonPrimitive(PrivacyNoticeHashing.CANONICALIZATION),
                "releaseBlockers" to JsonArray(releaseBlockers.map { JsonPrimitive(it) }),
                "notices" to JsonObject(notices),
            )
        )
        return pretty.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    companion object {
        /** Path of the web JSON, relative to `shared/`. */
        const val WEB_JSON = "generated/privacy-notices.json"
        private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }
    }
}

/**
 * Generates `PrivacyNotices.kt` (package core.catalog): `enum class PrivacyNotice` (a [CatalogEntry] with
 * `version`, `texts: List<StringResource>` in display order, and `hash(locale)`), plus `NoticeConsent`,
 * the `{notice_key, version, hash}` the app sends when recording a consent.
 */
class PrivacyNoticesKotlinGenerator(private val defs: SharedDefinitions) {
    fun generate(): String {
        val pn = PrivacyNotices(defs)
        val w = CodeWriter()
        w.raw(Naming.GENERATED_HEADER)
        w.line("@file:Suppress(\"unused\", \"RedundantVisibilityModifier\", \"SpellCheckingInspection\")")
        w.line()
        w.line("package ${Naming.CATALOG_PACKAGE}")
        w.line()
        w.line("import ${Naming.RES_PACKAGE}.*")
        w.line("import org.jetbrains.compose.resources.StringResource")
        w.line()
        w.line("/**")
        w.line(" * `catalog.privacyNotices`: versioned privacy notices (docs/PRIVACY.md). [texts] is the notice text in display")
        w.line(" * order; [hash] is the sha256 of that text for a locale (canonicalization v${PrivacyNoticeHashing.CANONICALIZATION},")
        w.line(" * docs/SHARED_DEFINITIONS.md). Send [consent] to the backend when the person agrees.")
        w.line(" */")
        w.line("enum class PrivacyNotice(")
        w.indent {
            line("override val key: String,")
            line("override val label: StringResource,")
            line("override val order: Int,")
            line("val version: Int,")
            line("val texts: List<StringResource>,")
            line("private val hashes: Map<String, String>,")
            line("override val deprecated: Boolean = false,")
        }
        w.line(") : CatalogEntry {")
        w.indent {
            for (n in pn.notices) {
                val texts = n.textKeys.joinToString(", ") { "Res.string.${Naming.stringResourceName(it)}" }
                val hashes = pn.hashes(n).entries.joinToString(", ") { "${Naming.kotlinString(it.key)} to ${Naming.kotlinString(it.value)}" }
                val args = mutableListOf(
                    Naming.kotlinString(n.key),
                    "Res.string.${Naming.stringResourceName(n.labelKey)}",
                    n.order.toString(),
                    "version = ${n.version}",
                    "texts = listOf($texts)",
                    "hashes = mapOf($hashes)",
                )
                if (n.deprecated) args += "deprecated = true"
                line("${Naming.enumConstant(n.key)}(${args.joinToString(", ")}),")
            }
            line(";")
            line()
            line("/** sha256 (64 lowercase hex) of this version's text in [locale]. */")
            line("fun hash(locale: AppLocale): String = hashes.getValue(locale.key)")
            line()
            line("/** What the backend's consent RPCs receive: `{notice_key, version, hash}` for the text shown in [locale]. */")
            line("fun consent(locale: AppLocale): NoticeConsent = NoticeConsent(key, version, hash(locale))")
            line()
            line("companion object {")
            indent {
                line("const val CANONICALIZATION: Int = ${PrivacyNoticeHashing.CANONICALIZATION}")
                line()
                line("fun fromKey(key: String): PrivacyNotice? = entries.firstOrNull { it.key == key }")
                line()
                line("/** Notices still shown (deprecated ones excluded), in display order. */")
                line("val active: List<PrivacyNotice> get() = entries.filterNot { it.deprecated }.sortedBy { it.order }")
            }
            line("}")
        }
        w.line("}")
        w.line()
        w.line("/** `{notice_key, version, hash}`: identifies exactly which notice text a person agreed to. */")
        w.line("data class NoticeConsent(val noticeKey: String, val version: Int, val hash: String)")
        return w.toString()
    }
}
