package com.sledje.agentpro.buildlogic.verify

import com.sledje.agentpro.buildlogic.codegen.CatalogList
import com.sledje.agentpro.buildlogic.codegen.Migrations
import com.sledje.agentpro.buildlogic.codegen.Naming
import com.sledje.agentpro.buildlogic.codegen.NoticeHashes
import com.sledje.agentpro.buildlogic.codegen.PrivacyNoticeHashing
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SiteSchemaKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.array
import com.sledje.agentpro.buildlogic.codegen.bool
import com.sledje.agentpro.buildlogic.codegen.double
import com.sledje.agentpro.buildlogic.codegen.long
import com.sledje.agentpro.buildlogic.codegen.obj
import com.sledje.agentpro.buildlogic.codegen.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import kotlin.math.roundToLong

/** Everything `verifyShared` looks at. Plain files, so the rules are unit-testable with fixtures. */
class VerifyInputs(
    val defs: SharedDefinitions,
    /** `composeApp/src/commonMain/composeResources` (hand-written strings and the icon drawables). */
    val composeResourcesDir: File,
    /** Source roots scanned for raw `.dp` spacing / raw `.sp` (e.g. composeApp/src, androidApp/src). */
    val kotlinSourceRoots: List<File>,
    val migrationsDir: File,
    val keysLock: Map<String, List<String>>,
    /** Paths in messages are shown relative to this directory. */
    val displayRoot: File,
    /** The lockfile's `privacyNoticeHashes` (notice key -> version -> locale -> hash). */
    val noticeLock: NoticeHashes = emptyMap(),
    /** `shared/generated/privacy-notices.json`; checked to be up to date when the catalog has privacyNotices. */
    val privacyNoticesJson: File? = null,
)

/**
 * The `verifyShared` rules (docs/SHARED_DEFINITIONS.md, Codegen). Each `check*` returns its violations;
 * [verify] runs them all. An empty list means the build may continue.
 */
class SharedVerifier(private val inputs: VerifyInputs) {
    private val defs get() = inputs.defs

    fun verify(): List<String> =
        checkRequiredStructure() +
            checkI18n() +
            checkTonesAndIcons() +
            checkKeys() +
            checkCatalogSync() +
            checkSwatchContrast() +
            checkTypeAndSpace() +
            checkSchemaAndTemplates() +
            checkHandWrittenStrings() +
            checkPrivacy() +
            checkKotlinSpacing()

    /**
     * Privacy notices and retention (docs/PRIVACY.md, docs/SHARED_DEFINITIONS.md "Privacy notices"):
     * - each notice has a whole `version` >= 1 and a non-empty `textKeys` list without duplicates (their i18n
     *   presence is checked by [checkI18n]);
     * - a notice version's text hashes match the locked ones (changed text needs a new version), are locked at
     *   all (run generateShared), and the version never goes below the newest locked one;
     * - `shared/generated/privacy-notices.json` is up to date;
     * - `retention`, when present, is an object whose leaves are whole numbers >= 0 or lists of them.
     */
    fun checkPrivacy(): List<String> {
        val v = mutableListOf<String>()
        val pn = PrivacyNotices(defs)
        defs.catalogList(PrivacyNoticeHashing.LIST)?.entries?.forEach { e ->
            val at = "catalog.privacyNotices.${e.string("key")}"
            val version = e.long("version")
            if (version == null || version < 1 || version > Int.MAX_VALUE) v += "$at: \"version\" must be a whole number >= 1"
            val textKeys = e.array("textKeys")
            when {
                textKeys.isNullOrEmpty() -> v += "$at: \"textKeys\" must be a non-empty array of i18n keys"
                textKeys.any { it.primitiveStringOrNull() == null } -> v += "$at: \"textKeys\" must contain only strings"
                else -> dupes(textKeys.map { it.primitiveStringOrNull()!! }).forEach { v += "$at: text key \"$it\" is listed twice" }
            }
        }
        for (n in pn.notices) {
            val at = "catalog.privacyNotices.${n.key}"
            val locked = inputs.noticeLock[n.key].orEmpty()
            val newest = locked.keys.maxOrNull()
            if (newest != null && n.version < newest) {
                v += "$at: version ${n.version} is lower than the newest locked version $newest"
                continue
            }
            val current = pn.hashes(n)
            if (current.size != pn.locales.size) continue // a missing text key; reported by checkI18n
            val lockedHashes = locked[n.version]
            if (lockedHashes == null) {
                v += "$at: version ${n.version} isn't in $CATALOG_LOCK; run ./gradlew generateShared and commit the lockfile"
                continue
            }
            current.forEach { (locale, hash) ->
                val old = lockedHashes[locale]
                if (old != null && old != hash) {
                    v += "$at: the $locale text of version ${n.version} changed after it was locked " +
                        "(locked ${old.take(12)}…, now ${hash.take(12)}…); give the notice a new \"version\""
                }
            }
        }
        val json = inputs.privacyNoticesJson
        if (pn.present && json != null) {
            val expected = pn.webJson()
            if (!json.isFile || json.readText() != expected) {
                v += "${rel(json)} is missing or out of date; run ./gradlew generateShared"
            }
        }
        defs.catalog["retention"]?.let { r ->
            if (r !is JsonObject) v += "catalog.json: \"retention\" must be an object"
            else retentionLeaves(r, "catalog.retention", v)
        }
        return v
    }

    /** Release gate (`verifyReleaseReady`, not part of `check`): no `{UPPER_CASE}` fill-in placeholder may remain. */
    fun checkReleaseReady(): List<String> = releaseBlockers(defs)

    private fun retentionLeaves(obj: JsonObject, at: String, v: MutableList<String>) {
        obj.forEach { (k, value) ->
            val where = "$at.$k"
            when (value) {
                is JsonObject -> retentionLeaves(value, where, v)
                is JsonArray -> if (value.isEmpty() || value.any { !it.isWholeNonNegative() }) v += "$where: must be a non-empty list of whole numbers >= 0"
                else -> if (!value.isWholeNonNegative()) v += "$where: must be a whole number >= 0"
            }
        }
    }

    fun checkRequiredStructure(): List<String> {
        val v = mutableListOf<String>()
        REQUIRED_LISTS.forEach { if (defs.catalogList(it) == null) v += "catalog.json: list \"$it\" is missing or empty" }
        if (defs.catalog["planSuggestions"] !is JsonArray) v += "catalog.json: \"planSuggestions\" must be an array of strings"
        if (defs.catalog["pricing"] !is JsonObject) v += "catalog.json: \"pricing\" must be an object"
        when (val reserved = defs.catalog["reservedSlugs"]) {
            !is JsonArray -> v += "catalog.json: \"reservedSlugs\" must be an array of strings"
            else -> {
                val slugs = reserved.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                slugs.forEachIndexed { i, slug ->
                    if (slug == null || !RESERVED_SLUG.matches(slug)) v += "catalog.reservedSlugs[$i]: must be a lowercase slug (a-z, 0-9, -)"
                }
                dupes(slugs.filterNotNull()).forEach { v += "catalog.reservedSlugs: duplicated slug \"$it\"" }
            }
        }
        if (templates().isEmpty()) v += "templates/index.json: \"templates\" must be a non-empty array"
        defs.catalogLists.forEach { list ->
            list.entries.forEachIndexed { i, e ->
                if (e.string("key").isNullOrEmpty()) v += "catalog.${list.jsonName}[$i]: missing \"key\""
                if (e.string("labelKey").isNullOrEmpty()) v += "catalog.${list.jsonName}[$i]: missing \"labelKey\""
            }
        }
        defs.catalogList("premiumModes")?.entries?.forEach { e ->
            if (e.long("months") == null) v += "catalog.premiumModes.${e.string("key")}: \"months\" must be a whole number"
        }
        return v
    }

    /** labelKeys present in en and hi; en/hi have identical keys, non-empty values and the same placeholders. */
    fun checkI18n(): List<String> {
        val v = mutableListOf<String>()
        val en = defs.i18nEn
        val hi = defs.i18nHi
        (en.keys - hi.keys).sorted().forEach { v += "i18n: \"$it\" is in en.json but missing from hi.json" }
        (hi.keys - en.keys).sorted().forEach { v += "i18n: \"$it\" is in hi.json but missing from en.json" }
        for ((key, value) in en) {
            if (value.isBlank()) v += "i18n/en.json: \"$key\" is empty"
            val h = hi[key] ?: continue
            if (h.isBlank()) v += "i18n/hi.json: \"$key\" is empty"
            val pe = PLACEHOLDER.findAll(value).map { it.value }.toSortedSet()
            val ph = PLACEHOLDER.findAll(h).map { it.value }.toSortedSet()
            if (pe != ph) v += "i18n: \"$key\" placeholders differ (en $pe, hi $ph)"
        }
        val byResource = (en.keys + hi.keys).groupBy { Naming.stringResourceName(it) }
        byResource.forEach { (res, keys) ->
            if (!Naming.RESOURCE_NAME.matches(res)) v += "i18n: key \"${keys.first()}\" can't become a resource name ($res)"
            if (keys.toSet().size > 1) v += "i18n: keys ${keys.toSet()} all map to resource $res"
        }
        for ((where, key) in referencedLabelKeys()) {
            if (key !in en) v += "$where: labelKey \"$key\" is missing from i18n/en.json"
            if (key !in hi) v += "$where: labelKey \"$key\" is missing from i18n/hi.json"
        }
        return v
    }

    /** Tones must be design-token tones; icons must exist as composeResources/drawable/ic_<name>.xml. */
    fun checkTonesAndIcons(): List<String> {
        val v = mutableListOf<String>()
        val tones = defs.designTokens.obj("tone")?.keys.orEmpty()
        defs.catalogLists.forEach { list ->
            list.entries.forEach { e ->
                e.string("tone")?.let { if (it !in tones) v += "catalog.${list.jsonName}.${e.string("key")}: unknown tone \"$it\" (allowed: ${tones.joinToString()})" }
            }
        }
        val drawableDir = File(inputs.composeResourcesDir, "drawable")
        for ((where, icon) in referencedIcons()) {
            when {
                !Naming.ICON_NAME.matches(icon) -> v += "$where: invalid icon name \"$icon\""
                !File(drawableDir, Naming.drawableName(icon) + ".xml").isFile ->
                    v += "$where: unknown icon \"$icon\" (missing ${rel(File(drawableDir, Naming.drawableName(icon) + ".xml"))})"
            }
        }
        return v
    }

    /** No duplicated keys; no key removed since it was locked; every key recorded in the lockfile. */
    fun checkKeys(): List<String> {
        val v = mutableListOf<String>()
        defs.catalogLists.forEach { list ->
            dupes(list.keys).forEach { v += "catalog.${list.jsonName}: duplicated key \"$it\"" }
            dupes(list.keys.map { Naming.enumConstant(it) }).forEach {
                v += "catalog.${list.jsonName}: several keys become the Kotlin name $it"
            }
            val locked = inputs.keysLock[list.jsonName].orEmpty()
            (locked - list.keys.toSet()).forEach {
                v += "catalog.${list.jsonName}: key \"$it\" was removed; keep it and mark it \"deprecated\": true"
            }
            (list.keys.toSet() - locked.toSet()).forEach {
                v += "catalog.${list.jsonName}: key \"$it\" isn't in ${CATALOG_LOCK}; run ./gradlew generateShared and commit the lockfile"
            }
        }
        (inputs.keysLock.keys - defs.catalogLists.map { it.jsonName }.toSet()).forEach {
            v += "catalog.json: list \"$it\" was removed (it is in $CATALOG_LOCK)"
        }
        dupes(sections().map { it.string("key").orEmpty() }).forEach { v += "site-schema: duplicated section \"$it\"" }
        sections().forEach { s ->
            fieldTree(s.array("fields"), "site-schema.${s.string("key")}") { where, fields ->
                dupes(fields.map { it.string("key").orEmpty() }).forEach { v += "$where: duplicated field \"$it\"" }
                fields.forEach { f ->
                    f.array("options")?.let { opts ->
                        dupes(opts.map { (it as? JsonObject)?.string("key").orEmpty() })
                            .forEach { v += "$where.${f.string("key")}: duplicated option \"$it\"" }
                    }
                }
            }
        }
        dupes(templates().map { it.string("key").orEmpty() }).forEach { v += "templates/index.json: duplicated template \"$it\"" }
        return v
    }

    /** The latest catalog_sync migration must carry the current hash (skipped while no schema migrations exist). */
    fun checkCatalogSync(): List<String> {
        val dir = inputs.migrationsDir
        if (Migrations.schemaMigrations(dir).isEmpty()) return emptyList()
        val latest = Migrations.latestCatalogSync(dir)
            ?: return listOf("${rel(dir)}: schema migrations exist but there is no *_catalog_sync.sql; run ./gradlew generateShared")
        val hash = Migrations.readHash(latest)
        return if (hash == defs.catalogHash) emptyList()
        else listOf("${rel(latest)}: catalog hash ${hash ?: "(none)"} doesn't match shared/ (${defs.catalogHash}); run ./gradlew generateShared and apply the new migration")
    }

    /** Every accent swatch keeps at least 4.5:1 contrast with white text. */
    fun checkSwatchContrast(): List<String> {
        val v = mutableListOf<String>()
        defs.catalogList("accentSwatches")?.entries?.forEach { e ->
            val hex = e.string("hex")
            val ratio = hex?.let { Contrast.ratioAgainstWhite(it) }
            when {
                ratio == null -> v += "catalog.accentSwatches.${e.string("key")}: invalid hex \"$hex\""
                ratio < 4.5 -> v += "catalog.accentSwatches.${e.string("key")}: contrast with white is ${"%.2f".format(ratio)} (< 4.5)"
            }
        }
        return v
    }

    /** text sizes = round(base x ratio^n), at most 4 sizes; exactly 3 spacing values. */
    fun checkTypeAndSpace(): List<String> {
        val v = mutableListOf<String>()
        val text = defs.designTokens.obj("text")
        if (text == null) v += "design-tokens.json: \"text\" is missing" else {
            val base = text.double("base")
            val ratio = text.double("ratio")
            val sizes = text.entries.mapNotNull { (k, e) -> (e as? JsonObject)?.double("size")?.let { k to it } }.sortedBy { it.second }
            if (base == null || ratio == null) v += "design-tokens.json: text.base and text.ratio are required"
            if (sizes.size > 4) v += "design-tokens.json: ${sizes.size} text sizes (${sizes.joinToString { it.first }}); at most 4 are allowed"
            if (base != null && ratio != null) sizes.forEachIndexed { n, (name, size) ->
                val expected = (base * ratio.pow(n)).roundToLong()
                if (size != expected.toDouble()) v += "design-tokens.json: text.$name is ${fmt(size)} but round($base x $ratio^$n) = $expected"
            }
        }
        val space = defs.designTokens.obj("space")
        if (space == null || space.size != 3) v += "design-tokens.json: \"space\" must have exactly 3 values (has ${space?.size ?: 0})"
        return v
    }

    fun checkSchemaAndTemplates(): List<String> {
        val v = mutableListOf<String>()
        sections().forEach { s ->
            fieldTree(s.array("fields"), "site-schema.${s.string("key")}") { where, fields ->
                fields.forEach { f ->
                    val at = "$where.${f.string("key")}"
                    val type = f.string("type")
                    if (type !in SiteSchemaKotlinGenerator.FIELD_TYPES) v += "$at: unknown field type \"$type\""
                    if ((type == "select" || type == "multiSelect") && f.array("options") == null && f.string("optionsFrom") == null)
                        v += "$at: $type needs \"options\" or \"optionsFrom\""
                    if (type == "list" && f.array("itemFields").isNullOrEmpty()) v += "$at: list needs \"itemFields\""
                    f.string("optionsFrom")?.let {
                        if (!it.startsWith("catalog.") || defs.catalogList(it.removePrefix("catalog.")) == null)
                            v += "$at: optionsFrom \"$it\" is not a catalog list"
                    }
                }
            }
        }
        val sectionKeys = sections().mapNotNull { it.string("key") }.toSet()
        val swatches = defs.catalogList("accentSwatches")?.keys.orEmpty().toSet()
        templates().forEach { t ->
            val at = "templates/index.json.${t.string("key")}"
            t.array("sections")?.forEach { s ->
                val key = (s as? JsonPrimitive)?.content
                if (key !in sectionKeys) v += "$at: unknown section \"$key\""
            }
            t.string("defaultAccent")?.let { if (it !in swatches) v += "$at: defaultAccent \"$it\" is not an accent swatch" }
        }
        return v
    }

    /** Every hand-written string in composeResources/values also exists in values-hi; `cat_` is reserved. */
    fun checkHandWrittenStrings(): List<String> {
        val v = mutableListOf<String>()
        val en = stringNames(File(inputs.composeResourcesDir, "values"))
        val hi = stringNames(File(inputs.composeResourcesDir, "values-hi"))
        (en.keys - hi.keys).sorted().forEach { v += "${en.getValue(it)}: string \"$it\" has no Hindi translation in values-hi/" }
        (en + hi).forEach { (name, file) ->
            if (name.startsWith("cat_")) v += "$file: \"$name\" uses the cat_ prefix, which is reserved for generated catalog strings"
        }
        return v
    }

    fun checkKotlinSpacing(): List<String> =
        inputs.kotlinSourceRoots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filterNot { KotlinSpacingScanner.isExempt(it) }
                .sortedBy { it.path }
                .flatMap { f -> KotlinSpacingScanner.scan(f.readText()).map { "${rel(f)}:${it}" } }
                .toList()
        }

    // ---- helpers --------------------------------------------------------------------------------

    private fun sections(): List<JsonObject> = defs.siteSchema.array("sections").orEmpty().filterIsInstance<JsonObject>()
    private fun templates(): List<JsonObject> = defs.templates.array("templates").orEmpty().filterIsInstance<JsonObject>()

    private fun fieldTree(fields: JsonArray?, where: String, visit: (String, List<JsonObject>) -> Unit) {
        val list = fields.orEmpty().filterIsInstance<JsonObject>()
        visit(where, list)
        list.forEach { f -> f.array("itemFields")?.let { fieldTree(it, "$where.${f.string("key")}", visit) } }
    }

    private fun referencedLabelKeys(): List<Pair<String, String>> {
        val refs = mutableListOf<Pair<String, String>>()
        defs.catalogLists.forEach { list ->
            list.entries.forEach { e -> e.string("labelKey")?.let { refs += "catalog.${list.jsonName}.${e.string("key")}" to it } }
        }
        sections().forEach { s ->
            val sk = "site-schema.${s.string("key")}"
            s.string("labelKey")?.let { refs += sk to it } ?: run { refs += sk to "(missing labelKey)" }
            fieldTree(s.array("fields"), sk) { where, fields ->
                fields.forEach { f ->
                    val at = "$where.${f.string("key")}"
                    refs += at to (f.string("labelKey") ?: "(missing labelKey)")
                    f.string("defaultKey")?.let { refs += "$at defaultKey" to it }
                    f.array("options")?.filterIsInstance<JsonObject>()?.forEach { o ->
                        refs += "$at option ${o.string("key")}" to (o.string("labelKey") ?: "(missing labelKey)")
                    }
                }
            }
        }
        defs.catalogList(PrivacyNoticeHashing.LIST)?.entries?.forEach { e ->
            e.array("textKeys")?.forEach { t ->
                t.primitiveStringOrNull()?.let { refs += "catalog.privacyNotices.${e.string("key")}.textKeys" to it }
            }
        }
        templates().forEach { t ->
            listOf("nameKey", "descriptionKey").forEach { k ->
                refs += "templates/index.json.${t.string("key")}.$k" to (t.string(k) ?: "(missing $k)")
            }
        }
        return refs
    }

    private fun referencedIcons(): List<Pair<String, String>> {
        val refs = mutableListOf<Pair<String, String>>()
        defs.catalogLists.forEach { list ->
            val needsIcon = list.jsonName !in LISTS_WITHOUT_ICONS
            list.entries.forEach { e ->
                val icon = e.string("icon")
                if (icon != null) refs += "catalog.${list.jsonName}.${e.string("key")}" to icon
                else if (needsIcon) refs += "catalog.${list.jsonName}.${e.string("key")}" to "(missing icon)"
            }
        }
        sections().forEach { s ->
            val sk = "site-schema.${s.string("key")}"
            refs += sk to (s.string("icon") ?: "(missing icon)")
            fieldTree(s.array("fields"), sk) { where, fields ->
                fields.forEach { f ->
                    f.array("options")?.filterIsInstance<JsonObject>()?.forEach { o ->
                        o.string("icon")?.let { refs += "$where.${f.string("key")} option ${o.string("key")}" to it }
                    }
                }
            }
        }
        return refs
    }

    private fun stringNames(dir: File): Map<String, String> {
        val names = LinkedHashMap<String, String>()
        dir.listFiles { f -> f.isFile && f.extension == "xml" }?.sortedBy { it.name }?.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n.nodeName in setOf("string", "plurals", "string-array")) {
                    n.attributes?.getNamedItem("name")?.nodeValue?.let { names.putIfAbsent(it, rel(file)) }
                }
            }
        }
        return names
    }

    private fun rel(f: File): String = f.relativeToOrNull(inputs.displayRoot)?.path ?: f.path
    private fun dupes(keys: List<String>) = keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
    private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    companion object {
        val REQUIRED_LISTS = listOf(
            "enquiryStatuses", "interests", "enquirySources", "premiumModes", "policyStatuses",
            "dueBuckets", "planStatuses", "domainStatuses", "accentSwatches", "locales", "features",
        )
        /** Catalog lists whose entries have no icon. */
        val LISTS_WITHOUT_ICONS = setOf("accentSwatches", "locales", PrivacyNoticeHashing.LIST)
        private val RESERVED_SLUG = Regex("^[a-z0-9-]+$")
        const val CATALOG_LOCK = "mobile/build-logic/catalog-keys.lock.json"
        private val PLACEHOLDER = Regex("\\{[A-Za-z0-9_]+}")

        /** Every i18n value that still holds a `{UPPER_CASE}` fill-in placeholder, e.g. `{GRIEVANCE_EMAIL}`. */
        fun releaseBlockers(defs: SharedDefinitions): List<String> =
            listOf("en" to defs.i18nEn, "hi" to defs.i18nHi).flatMap { (locale, dict) ->
                dict.entries.sortedBy { it.key }.mapNotNull { (key, value) ->
                    val tokens = PrivacyNoticeHashing.RELEASE_PLACEHOLDER.findAll(value).map { it.value }.distinct().toList()
                    if (tokens.isEmpty()) null
                    else "i18n/$locale.json: \"$key\" still contains ${tokens.joinToString()}; fill it in before a release (docs/SHARED_DEFINITIONS.md, Release gate)"
                }
            }
    }
}

private fun kotlinx.serialization.json.JsonElement.primitiveStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun kotlinx.serialization.json.JsonElement.isWholeNonNegative(): Boolean =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()?.let { it >= 0 } == true

/** WCAG 2.x contrast. */
object Contrast {
    fun ratioAgainstWhite(hex: String): Double? {
        val h = hex.removePrefix("#")
        if (h.length != 6 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val l = luminance(h)
        return (1.0 + 0.05) / (l + 0.05)
    }

    private fun luminance(h: String): Double {
        fun channel(i: Int): Double {
            val c = h.substring(i, i + 2).toInt(16) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }
}
