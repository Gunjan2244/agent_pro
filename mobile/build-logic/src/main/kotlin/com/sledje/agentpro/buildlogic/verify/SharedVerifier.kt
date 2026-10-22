package com.sledje.agentpro.buildlogic.verify

import com.sledje.agentpro.buildlogic.codegen.CatalogList
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.codegen.Migrations
import com.sledje.agentpro.buildlogic.codegen.Naming
import com.sledje.agentpro.buildlogic.codegen.NoticeHashes
import com.sledje.agentpro.buildlogic.codegen.PrivacyNoticeHashing
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.PublicText
import com.sledje.agentpro.buildlogic.codegen.SeedForm
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SiteSchemaKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.SiteSeedJson
import com.sledje.agentpro.buildlogic.codegen.TemplatesKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.array
import com.sledje.agentpro.buildlogic.codegen.bool
import com.sledje.agentpro.buildlogic.codegen.double
import com.sledje.agentpro.buildlogic.codegen.long
import com.sledje.agentpro.buildlogic.codegen.obj
import com.sledje.agentpro.buildlogic.codegen.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.text.Normalizer
import java.util.Locale
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
            checkOffers() +
            checkTemplateFamilies() +
            checkPublicText() +
            checkSeed() +
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
        defs.catalogLists.forEach { list ->
            list.entries.forEach { e ->
                if (e.containsKey(IMAGE)) {
                    val name = e.string(IMAGE)
                    if (name == null || !IMAGE_NAME.matches(name))
                        v += "catalog.${list.jsonName}.${e.string("key")}: \"$IMAGE\" must be a file base name (a-z, 0-9, -), e.g. \"interest-term\""
                }
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
                    checkFieldRules(f, at, type, inList = where.count { it == '.' } > 1, v)
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

    /**
     * The site-schema field rules added for offers (docs/OFFERS.md, "New field rules"): `generated` is "uuid" on a
     * text field, `equals` is `true` on a toggle, `maxDaysAhead` is a whole number >= 1 on a date, `requiredIf` is for
     * admin forms (offer-schema.json) only, and `offerRef`/`offerList` sit at section level (`offerList` with maxItems).
     */
    private fun checkFieldRules(f: JsonObject, at: String, type: String?, inList: Boolean, v: MutableList<String>) {
        f["generated"]?.let { if (f.string("generated") != "uuid" || type != "text") v += "$at: \"generated\" must be \"uuid\" on a text field" }
        f["equals"]?.let { if (f.bool("equals") != true || type != "toggle") v += "$at: \"equals\" must be true on a toggle" }
        f["maxDaysAhead"]?.let { if ((f.long("maxDaysAhead") ?: 0) < 1 || type != "date") v += "$at: \"maxDaysAhead\" must be a whole number >= 1 on a date" }
        if (f.containsKey("requiredIf")) v += "$at: \"requiredIf\" is only for offer-schema.json (admin forms)"
        if (type == "offerRef" || type == "offerList") {
            if (inList) v += "$at: $type can't be inside a list"
            if (type == "offerList" && (f.long("maxItems") ?: 0) < 1) v += "$at: offerList needs \"maxItems\""
        }
    }

    /**
     * Companies and offers (docs/OFFERS.md):
     * - `offerCtaKinds` entries carry a boolean `agentAllowed`, and at least one kind is allowed for agents;
     * - `insuranceTypes`, when present, has at least one active entry (memberships need one; DECISIONS #89);
     * - `catalog.offers` holds whole numbers >= 1 and an `imageAspect` like "16:9";
     * - offer-schema.json: whole `version` >= 1; non-empty `localized` (text/multiline only) and `fields`
     *   ([OFFER_FIELD_TYPES]); unique keys; selects with a catalog `optionsFrom`; `requiredIf` naming another field and,
     *   for a catalog select, one of its keys; the image aspect equal to `catalog.offers.imageAspect`. (Its labelKeys
     *   are checked by [checkI18n].)
     * - site-schema `offerList.maxItems` never exceeds `offers.maxSectionItems`, nor `maxDaysAhead` `offers.maxValidityDays`.
     */
    fun checkOffers(): List<String> {
        val v = mutableListOf<String>()
        defs.catalogList("offerCtaKinds")?.let { kinds ->
            kinds.entries.forEach { e ->
                if (e.bool(SiteSchemaKotlinGenerator.AGENT_ALLOWED) == null) v += "catalog.offerCtaKinds.${e.string("key")}: \"agentAllowed\" must be true or false"
            }
            if (kinds.entries.none { it.bool(SiteSchemaKotlinGenerator.AGENT_ALLOWED) == true && it.bool("deprecated") != true })
                v += "catalog.offerCtaKinds: no active kind is agentAllowed"
        }
        // Every pending or approved company membership needs at least one type (DECISIONS #89).
        val insuranceTypes = defs.catalog[CatalogSyncGenerator.INSURANCE_TYPES]
        if (insuranceTypes != null && (defs.catalogList(CatalogSyncGenerator.INSURANCE_TYPES)?.activeKeys).isNullOrEmpty())
            v += "catalog.insuranceTypes: needs at least one active entry"
        // Each interest names the insurance type it belongs to, or null for one that is always available (DECISIONS #93).
        // Required once any interest has it or the seed derives services from the types.
        val interestEntries = defs.catalogList("interests")?.entries.orEmpty()
        val typed = interestEntries.any { it.containsKey(INSURANCE_TYPE) } || defs.siteSeed?.let { SiteSeedJson.insuranceTypes(it) } != null
        if (insuranceTypes != null && typed) {
            val typeKeys = defs.catalogList(CatalogSyncGenerator.INSURANCE_TYPES)?.keys.orEmpty().toSet()
            interestEntries.forEach { e ->
                val at = "catalog.interests.${e.string("key")}"
                when (val t = e[INSURANCE_TYPE]) {
                    null -> v += "$at: needs \"$INSURANCE_TYPE\" (an insuranceTypes key, or null for an interest of every type)"
                    is JsonPrimitive -> if (t !is kotlinx.serialization.json.JsonNull && (!t.isString || t.content !in typeKeys))
                        v += "$at.$INSURANCE_TYPE: \"${t.content}\" is not an insuranceTypes key"
                    else -> v += "$at.$INSURANCE_TYPE: must be a string or null"
                }
            }
        }
        val limits = defs.catalog["offers"]
        if (limits != null && limits !is JsonObject) v += "catalog.json: \"offers\" must be an object"
        (limits as? JsonObject)?.forEach { (k, value) ->
            val ok = if (k == "imageAspect") (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.matches(ASPECT) == true
            else (value as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()?.let { it >= 1 } == true
            if (!ok) v += "catalog.offers.$k: must be ${if (k == "imageAspect") "an aspect such as \"16:9\"" else "a whole number >= 1"}"
        }
        val maxItems = (limits as? JsonObject)?.long("maxSectionItems")
        val maxDays = (limits as? JsonObject)?.long("maxValidityDays")
        sections().forEach { sec ->
            fieldTree(sec.array("fields"), "site-schema.${sec.string("key")}") { where, fields ->
                fields.forEach { f ->
                    val at = "$where.${f.string("key")}"
                    if (f.string("type") == "offerList" && maxItems != null && (f.long("maxItems") ?: 0) > maxItems)
                        v += "$at: maxItems ${f.long("maxItems")} is more than catalog.offers.maxSectionItems ($maxItems)"
                    if (maxDays != null && (f.long("maxDaysAhead") ?: 0) > maxDays)
                        v += "$at: maxDaysAhead ${f.long("maxDaysAhead")} is more than catalog.offers.maxValidityDays ($maxDays)"
                }
            }
        }
        val schema = defs.offerSchema ?: return v
        val at = "offer-schema.json"
        if ((schema.long("version") ?: 0) < 1) v += "$at: \"version\" must be a whole number >= 1"
        val localized = schema.array("localized").orEmpty().filterIsInstance<JsonObject>()
        val fields = schema.array("fields").orEmpty().filterIsInstance<JsonObject>()
        if (localized.isEmpty()) v += "$at: \"localized\" must be a non-empty array of fields"
        if (fields.isEmpty()) v += "$at: \"fields\" must be a non-empty array of fields"
        dupes((localized + fields).map { it.string("key").orEmpty() }).forEach { v += "$at: duplicated field \"$it\"" }
        localized.forEach { f ->
            val where = "$at.localized.${f.string("key")}"
            if (f.string("type") !in setOf("text", "multiline")) v += "$where: a localized field must be text or multiline"
            if ((f.long("maxLength") ?: 0) < 1) v += "$where: needs \"maxLength\""
            if (f.containsKey(ADMIN_ONLY)) v += "$where: \"$ADMIN_ONLY\" is only for non-localized fields (localized text is public)"
        }
        val byKey = fields.associateBy { it.string("key").orEmpty() }
        fields.forEach { f ->
            val where = "$at.fields.${f.string("key")}"
            val type = f.string("type")
            if (f.containsKey(ADMIN_ONLY) && f.bool(ADMIN_ONLY) == null) v += "$where: \"$ADMIN_ONLY\" must be true or false"
            if (type !in OFFER_FIELD_TYPES) v += "$where: unknown field type \"$type\" (allowed: ${OFFER_FIELD_TYPES.joinToString()})"
            val from = f.string("optionsFrom")
            if (type == "select" && from == null) v += "$where: select needs \"optionsFrom\""
            if (from != null && (!from.startsWith("catalog.") || defs.catalogList(from.removePrefix("catalog.")) == null))
                v += "$where: optionsFrom \"$from\" is not a catalog list"
            if (type == "image") {
                val expected = (limits as? JsonObject)?.string("imageAspect")
                if (expected != null && f.string("aspect") != expected) v += "$where: aspect must be catalog.offers.imageAspect (\"$expected\")"
            }
            f["requiredIf"]?.let { cond ->
                val (other, value) = (cond as? JsonObject)?.entries?.singleOrNull()?.let { it.key to (it.value as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    ?: (null to null)
                val target = other?.let { byKey[it] }
                when {
                    other == null || value == null -> v += "$where: \"requiredIf\" must be one {\"field\": \"value\"} pair"
                    target == null || other == f.string("key") -> v += "$where: requiredIf names unknown field \"$other\""
                    else -> target.string("optionsFrom")?.removePrefix("catalog.")?.let { defs.catalogList(it) }?.let { list ->
                        if (value !in list.keys) v += "$where: requiredIf value \"$value\" is not a key of ${target.string("optionsFrom")}"
                    }
                }
            }
        }
        return v
    }

    /**
     * Template families (DECISIONS #76): with `catalog.templateFamilies`, every template names one of its keys in
     * `family` and every active family has at least one template; without the list, no template may name a family.
     */
    fun checkTemplateFamilies(): List<String> {
        val v = mutableListOf<String>()
        val families = defs.catalogList(TemplatesKotlinGenerator.FAMILIES)
        val keys = families?.keys.orEmpty().toSet()
        templates().forEach { t ->
            val at = "templates/index.json.${t.string("key")}"
            val family = t.string("family")
            when {
                families == null -> if (t.containsKey("family")) v += "$at: \"family\" needs catalog.templateFamilies"
                family == null -> v += "$at: \"family\" is missing (one of ${keys.joinToString()})"
                family !in keys -> v += "$at: unknown family \"$family\" (allowed: ${keys.joinToString()})"
            }
        }
        families?.entries?.filterNot { it.bool("deprecated") == true }?.forEach { e ->
            val key = e.string("key")
            if (templates().none { it.string("family") == key }) v += "catalog.templateFamilies.$key: no template has this family"
        }
        return v
    }

    /**
     * Public site text (DECISIONS #77): `catalog.publicTextRules.blockedTerms` is a non-empty list of unique, trimmed,
     * lowercase, NFC strings with no single generic word ([PublicText.GENERIC_SINGLE_WORDS]); and no `site.*` or
     * `seed.*` i18n text contains a blocked term ([PublicText.violations]: word-bounded, case-insensitive).
     */
    fun checkPublicText(): List<String> {
        val rules = defs.catalog["publicTextRules"] ?: return emptyList()
        val v = mutableListOf<String>()
        val at = "catalog.publicTextRules"
        val list = (rules as? JsonObject)?.get("blockedTerms") as? JsonArray
        if (rules !is JsonObject || list.isNullOrEmpty()) return listOf("$at: must be an object with a non-empty \"blockedTerms\" array")
        val terms = list.mapIndexedNotNull { i, e ->
            val term = (e as? JsonPrimitive)?.takeIf { it.isString }?.content
            when {
                term == null -> { v += "$at.blockedTerms[$i]: must be a string"; null }
                term.isBlank() -> { v += "$at.blockedTerms[$i]: is empty"; null }
                else -> {
                    if (term != term.trim() || "  " in term) v += "$at.blockedTerms[$i]: \"$term\" has extra spaces"
                    if (term != term.lowercase(Locale.ROOT)) v += "$at.blockedTerms[$i]: \"$term\" must be lowercase"
                    if (term != Normalizer.normalize(term, Normalizer.Form.NFC)) v += "$at.blockedTerms[$i]: \"$term\" must be NFC-normalized"
                    if (term.trim().lowercase(Locale.ROOT) in PublicText.GENERIC_SINGLE_WORDS)
                        v += "$at.blockedTerms[$i]: \"$term\" is a generic everyday word; block multi-word names only"
                    term
                }
            }
        }
        dupes(terms.map { it.trim().lowercase(Locale.ROOT) }).forEach { v += "$at.blockedTerms: duplicated term \"$it\"" }
        for (locale in listOf("en", "hi")) {
            val dict = defs.i18n(locale) ?: continue
            dict.entries.sortedBy { it.key }.filter { it.key.startsWith("site.") || it.key.startsWith("seed.") }.forEach { (key, text) ->
                PublicText.violations(text, terms).forEach { v += "i18n/$locale.json: \"$key\" contains the blocked term \"$it\"" }
            }
        }
        return v
    }

    /**
     * `site-seed.json` (DECISIONS #78; docs/SHARED_DEFINITIONS.md, site-seed.json), when present:
     * - whole `version` >= 1; `placeholders` map to [SiteSeedJson.PROFILE_FIELDS]; `requiredPlaceholders` are placeholders;
     * - `visibility.hidden` names non-pinned schema sections; `visibleOnlyInFamily` names sections and template families;
     * - every seeded section/field exists in site-schema.json and has exactly one valid form: `variants` (text and
     *   multiline fields only), `fromProfile`, `value`/`byLocale` (valid for the field type and its options; byLocale
     *   covers every active locale) or `items` (list fields only; at most `maxItems`, each with its required item fields);
     * - every variant key (and fallback) exists in every locale; uses only declared placeholders; a text using an
     *   optional placeholder has a fallback that uses only required ones; and with [SAMPLE_PLACEHOLDERS] filled in it fits
     *   the field's `maxLength` in every locale.
     */
    fun checkSeed(): List<String> {
        val seed = defs.siteSeed ?: return emptyList()
        val v = mutableListOf<String>()
        val at = "site-seed.json"
        if ((seed.long("version") ?: 0) < 1) v += "$at: \"version\" must be a whole number >= 1"
        val placeholders = SiteSeedJson.placeholders(seed)
        if (placeholders.isEmpty()) v += "$at: \"placeholders\" must map each placeholder to a profile field"
        placeholders.forEach { (p, field) ->
            if (field !in SiteSeedJson.PROFILE_FIELDS) v += "$at.placeholders.$p: \"$field\" is not a profile field (${SiteSeedJson.PROFILE_FIELDS.joinToString()})"
        }
        val required = SiteSeedJson.requiredPlaceholders(seed).toSet()
        (required - placeholders.keys).forEach { v += "$at.requiredPlaceholders: \"$it\" is not in \"placeholders\"" }
        val sectionsByKey = sections().associateBy { it.string("key").orEmpty() }
        SiteSeedJson.hidden(seed).forEach {
            val s = sectionsByKey[it]
            if (s == null) v += "$at.visibility.hidden: unknown section \"$it\""
            else if (s.bool("pinned") == true) v += "$at.visibility.hidden: \"$it\" is pinned and can't be hidden"
        }
        val familyKeys = defs.catalogList(TemplatesKotlinGenerator.FAMILIES)?.keys.orEmpty().toSet()
        SiteSeedJson.visibleOnlyInFamily(seed).forEach { (section, family) ->
            if (section !in sectionsByKey) v += "$at.visibility.visibleOnlyInFamily: unknown section \"$section\""
            if (family !in familyKeys) v += "$at.visibility.visibleOnlyInFamily.$section: unknown template family \"$family\""
        }
        val mixKeys = checkSeedInsuranceTypes(seed, v)
        val ctx = SeedContext(placeholders.keys, required, activeLocales(), v, mixKeys)
        SiteSeedJson.serviceDescriptions(seed).forEach { (interest, form) ->
            val desc = sectionsByKey["services"]?.array("fields").orEmpty().filterIsInstance<JsonObject>().firstOrNull { it.string("key") == "items" }
                ?.array("itemFields").orEmpty().filterIsInstance<JsonObject>().firstOrNull { it.string("key") == SiteSeedJson.ITEM_DESCRIPTION }
            val where = "$at.insuranceTypes.serviceDescriptions.$interest"
            if (desc == null) v += "$where: site-schema services.items has no \"${SiteSeedJson.ITEM_DESCRIPTION}\" item field"
            else if (form !is SeedForm.Variants) v += "$where: must be {\"variants\": [...]}"
            else checkSeedForm(form, desc, where, ctx)
        }
        SiteSeedJson.sections(seed).forEach { (sectionKey, fields) ->
            val section = sectionsByKey[sectionKey]
            if (section == null) { v += "$at.sections: unknown section \"$sectionKey\""; return@forEach }
            val schemaFields = section.array("fields").orEmpty().filterIsInstance<JsonObject>().associateBy { it.string("key").orEmpty() }
            fields.forEach { (fieldKey, json) ->
                val where = "$at.$sectionKey.$fieldKey"
                val field = schemaFields[fieldKey]
                if (field == null) v += "$where: unknown field of section $sectionKey"
                else checkSeedForm(SiteSeedJson.form(json), field, where, ctx)
            }
        }
        return v
    }

    private class SeedContext(
        val placeholders: Set<String>, val required: Set<String>, val locales: List<String>, val v: MutableList<String>,
        /** The declared copy mixes; null when site-seed.json has no `insuranceTypes`. */
        val mixes: Set<String>?,
    )

    /**
     * `site-seed.json` `insuranceTypes` (DECISIONS #93): every active catalog insurance type has at least one seed
     * interest, each an active catalog interest of that type, listed once; no unknown type; `whenNone` non-empty and
     * made of active types; every seed interest has a service description; `mixes` non-empty with unique keys, valid
     * `onlyTypes`, and exactly the last one without `onlyTypes` (it matches everything). Returns the mix keys (null when absent).
     */
    private fun checkSeedInsuranceTypes(seed: JsonObject, v: MutableList<String>): Set<String>? {
        val block = SiteSeedJson.insuranceTypes(seed) ?: return null
        val at = "site-seed.json.insuranceTypes"
        val types = defs.catalogList(CatalogSyncGenerator.INSURANCE_TYPES)
        if (types == null) { v += "$at: catalog.json has no insuranceTypes list"; return SiteSeedJson.mixes(seed).map { it.first }.toSet() }
        val active = types.activeKeys.toSet()
        val interests = defs.catalogList("interests")?.entries.orEmpty().associateBy { it.string("key").orEmpty() }
        val seedInterests = SiteSeedJson.seedInterests(seed)
        (active - seedInterests.keys).forEach { v += "$at.seedInterests: insurance type \"$it\" needs at least one seed interest" }
        seedInterests.forEach { (type, list) ->
            if (type !in active) v += "$at.seedInterests: \"$type\" is not an active insurance type"
            if (list.isEmpty()) v += "$at.seedInterests.$type: needs at least one interest"
            dupes(list).forEach { v += "$at.seedInterests.$type: \"$it\" is listed twice" }
            list.forEach { k ->
                val e = interests[k]
                when {
                    e == null -> v += "$at.seedInterests.$type: unknown interest \"$k\""
                    e.bool("deprecated") == true -> v += "$at.seedInterests.$type: interest \"$k\" is deprecated"
                    e.string(INSURANCE_TYPE) != type -> v += "$at.seedInterests.$type: interest \"$k\" belongs to insurance type \"${e.string(INSURANCE_TYPE)}\""
                }
            }
        }
        val descriptions = SiteSeedJson.serviceDescriptions(seed)
        (seedInterests.values.flatten().toSet() - descriptions.keys).forEach { v += "$at.serviceDescriptions: seed interest \"$it\" has no description" }
        (descriptions.keys - interests.keys).forEach { v += "$at.serviceDescriptions: unknown interest \"$it\"" }
        val whenNone = SiteSeedJson.whenNone(seed)
        if (whenNone.isEmpty()) v += "$at.whenNone: needs at least one insurance type"
        (whenNone - active).forEach { v += "$at.whenNone: \"$it\" is not an active insurance type" }
        val mixes = SiteSeedJson.mixes(seed)
        if (mixes.isEmpty()) v += "$at.mixes: needs at least one mix"
        dupes(mixes.map { it.first }).forEach { v += "$at.mixes: \"$it\" is listed twice" }
        mixes.forEachIndexed { i, (key, only) ->
            val last = i == mixes.lastIndex
            if (only == null && !last) v += "$at.mixes.$key: only the last mix may leave out \"onlyTypes\""
            if (only != null && last) v += "$at.mixes.$key: the last mix must leave out \"onlyTypes\", so every agent gets one"
            only?.let { (it - active).forEach { t -> v += "$at.mixes.$key.onlyTypes: \"$t\" is not an active insurance type" } }
        }
        return mixes.map { it.first }.toSet()
    }

    private fun checkSeedForm(form: SeedForm, field: JsonObject, where: String, ctx: SeedContext) {
        val v = ctx.v
        val type = field.string("type")
        when (form) {
            is SeedForm.Invalid -> v += "$where: ${form.reason}"
            is SeedForm.FromProfile -> if (form.field !in SiteSeedJson.PROFILE_FIELDS)
                v += "$where: fromProfile \"${form.field}\" is not a profile field (${SiteSeedJson.PROFILE_FIELDS.joinToString()})"
            is SeedForm.Literal -> checkSeedValue(form.value, field, where, v)
            is SeedForm.ByLocale -> {
                (ctx.locales - form.values.keys).forEach { v += "$where: byLocale has no value for locale \"$it\"" }
                (form.values.keys - ctx.locales.toSet()).forEach { v += "$where: byLocale names unknown locale \"$it\"" }
                form.values.forEach { (l, value) -> checkSeedValue(value, field, "$where.$l", v) }
            }
            is SeedForm.Variants -> {
                if (type != "text" && type != "multiline") { v += "$where: variants are only for text and multiline fields"; return }
                val maxLength = field.long("maxLength")
                form.variants.forEach { variant ->
                    checkSeedText(variant.key, maxLength, where, ctx, isFallback = false, hasFallback = variant.fallback != null)
                    variant.fallback?.let { checkSeedText(it, maxLength, "$where (fallback of ${variant.key})", ctx, isFallback = true, hasFallback = false) }
                }
            }
            is SeedForm.ByMix -> {
                val mixes = ctx.mixes
                if (mixes == null) { v += "$where: byMix needs \"insuranceTypes.mixes\""; return }
                (mixes - form.values.keys).forEach { v += "$where: byMix has no value for mix \"$it\"" }
                (form.values.keys - mixes).forEach { v += "$where: byMix names unknown mix \"$it\"" }
                form.values.forEach { (mix, f) -> checkSeedForm(f, field, "$where.$mix", ctx) }
            }
            is SeedForm.FromInsuranceTypes -> {
                if (ctx.mixes == null) v += "$where: fromInsuranceTypes needs the \"insuranceTypes\" block"
                val ok = when (form.kind) {
                    SiteSeedJson.INTEREST_KEYS -> type == "multiSelect" && field.string("optionsFrom") == "catalog.interests"
                    else -> type == "list" && field.array("itemFields").orEmpty().filterIsInstance<JsonObject>().let { items ->
                        items.any { it.string("key") == SiteSeedJson.ITEM_INTEREST && it.string("optionsFrom") == "catalog.interests" } &&
                            items.any { it.string("key") == SiteSeedJson.ITEM_DESCRIPTION && it.string("type") in setOf("text", "multiline") }
                    }
                }
                if (!ok) v += "$where: fromInsuranceTypes \"${form.kind}\" needs " + if (form.kind == SiteSeedJson.INTEREST_KEYS)
                    "a multiSelect with optionsFrom catalog.interests" else "a list with an \"interest\" select from catalog.interests and a text \"description\""
            }
            is SeedForm.Items -> {
                if (type != "list") { v += "$where: items are only for list fields"; return }
                val maxItems = field.long("maxItems")
                if (maxItems != null && form.items.size > maxItems) v += "$where: ${form.items.size} items is more than maxItems ($maxItems)"
                val itemFields = field.array("itemFields").orEmpty().filterIsInstance<JsonObject>().associateBy { it.string("key").orEmpty() }
                form.items.forEachIndexed { i, item ->
                    itemFields.values.filter { it.bool("required") == true && it.string("generated") == null }.forEach { f ->
                        if (f.string("key") !in item) v += "$where[$i]: required item field \"${f.string("key")}\" is missing"
                    }
                    item.forEach { (k, f) ->
                        val itemField = itemFields[k]
                        if (itemField == null) v += "$where[$i]: unknown item field \"$k\""
                        else checkSeedForm(f, itemField, "$where[$i].$k", ctx)
                    }
                }
            }
        }
    }

    private fun checkSeedText(key: String, maxLength: Long?, where: String, ctx: SeedContext, isFallback: Boolean, hasFallback: Boolean) {
        val v = ctx.v
        if (!key.startsWith("seed.")) v += "$where: seed text key \"$key\" must start with \"seed.\""
        for (locale in ctx.locales) {
            val text = defs.i18n(locale)?.get(key)
            if (text == null) { v += "$where: i18n key \"$key\" is missing from i18n/$locale.json"; continue }
            val used = SiteSeedJson.PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toSet()
            (used - ctx.placeholders).forEach { v += "$where: \"$key\" ($locale) uses unknown placeholder {$it}" }
            val optional = used - ctx.required
            if (isFallback && optional.isNotEmpty()) v += "$where: fallback \"$key\" ($locale) may only use ${ctx.required.joinToString { "{$it}" }}, not ${optional.joinToString { "{$it}" }}"
            if (!isFallback && !hasFallback && optional.isNotEmpty()) v += "$where: \"$key\" ($locale) uses ${optional.joinToString { "{$it}" }}, which can be blank; give it a \"fallback\""
            val filled = SiteSeedJson.PLACEHOLDER.replace(text) { SAMPLE_PLACEHOLDERS[it.groupValues[1]] ?: it.value }
            if (maxLength != null && filled.length > maxLength)
                v += "$where: \"$key\" ($locale) is ${filled.length} characters with sample values, more than maxLength $maxLength"
        }
    }

    private fun checkSeedValue(value: JsonElement, field: JsonObject, where: String, v: MutableList<String>) {
        val type = field.string("type")
        val options = optionKeys(field)
        fun str(e: JsonElement) = (e as? JsonPrimitive)?.takeIf { it.isString }?.content
        when (type) {
            "select" -> {
                val s = str(value)
                if (s == null || (options != null && s !in options)) v += "$where: $value is not an option of this select (${options?.joinToString()})"
            }
            "multiSelect" -> {
                val arr = value as? JsonArray
                if (arr == null) v += "$where: a multiSelect value must be an array of option keys"
                else arr.forEach { e ->
                    val s = str(e)
                    if (s == null || (options != null && s !in options)) v += "$where: $e is not an option (${options?.joinToString()})"
                }
                arr?.mapNotNull { str(it) }?.let { keys -> dupes(keys).forEach { v += "$where: option \"$it\" is listed twice" } }
            }
            "toggle" -> {
                val p = (value as? JsonPrimitive)?.takeIf { !it.isString }
                if (p == null || p.content !in setOf("true", "false")) v += "$where: a toggle value must be true or false"
            }
            "number" -> {
                val p = (value as? JsonPrimitive)?.takeIf { !it.isString }
                if (p == null || p.content.toDoubleOrNull() == null) v += "$where: a number value must be a number"
            }
            "list", "offerRef", "offerList", "image" -> v += "$where: a $type field can't have a fixed value"
            else -> {
                val s = str(value)
                if (s == null) v += "$where: a $type value must be a string"
                else field.long("maxLength")?.let { if (s.length > it) v += "$where: value is longer than maxLength $it" }
            }
        }
    }

    /** Option keys of a select/multiSelect (inline options or the catalog list's keys); null when it has neither. */
    private fun optionKeys(field: JsonObject): Set<String>? {
        field.array("options")?.let { opts -> return opts.mapNotNull { (it as? JsonObject)?.string("key") }.toSet() }
        val from = field.string("optionsFrom") ?: return null
        val list = defs.catalogList(from.removePrefix("catalog.")) ?: return emptySet()
        val agentOnly = list.entries.any { it.containsKey(SiteSchemaKotlinGenerator.AGENT_ALLOWED) }
        return list.entries.filter { it.bool("deprecated") != true && (!agentOnly || it.bool(SiteSchemaKotlinGenerator.AGENT_ALLOWED) == true) }
            .mapNotNull { it.string("key") }.toSet()
    }

    private fun activeLocales(): List<String> =
        defs.catalogList("locales")?.entries.orEmpty().filterNot { it.bool("deprecated") == true }
            .sortedBy { it.long("order") ?: 0 }.mapNotNull { it.string("key") }

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
            list.entries.forEach { e ->
                e.string("labelKey")?.let { refs += "catalog.${list.jsonName}.${e.string("key")}" to it }
                e.string("descriptionKey")?.let { refs += "catalog.${list.jsonName}.${e.string("key")}.descriptionKey" to it }
            }
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
        defs.offerSchema?.let { schema ->
            listOf("localized", "fields").forEach { group ->
                schema.array(group).orEmpty().filterIsInstance<JsonObject>().forEach { f ->
                    refs += "offer-schema.json.$group.${f.string("key")}" to (f.string("labelKey") ?: "(missing labelKey)")
                }
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
        /** `catalog.interests[].insuranceType` (DECISIONS #93). */
        const val INSURANCE_TYPE = "insuranceType"
        val REQUIRED_LISTS = listOf(
            "enquiryStatuses", "interests", "enquirySources", "premiumModes", "policyStatuses",
            "dueBuckets", "planStatuses", "domainStatuses", "accentSwatches", "locales", "features",
        )
        /** Catalog lists whose entries have no icon. */
        val LISTS_WITHOUT_ICONS = setOf("accentSwatches", "locales", PrivacyNoticeHashing.LIST)
        private val RESERVED_SLUG = Regex("^[a-z0-9-]+$")
        /** Optional catalog entry field (interests): the base name of web/images/<name>-{400,800}.webp. The files aren't checked. */
        const val IMAGE = "image"
        private val IMAGE_NAME = Regex("^[a-z0-9-]+$")
        private val ASPECT = Regex("^[1-9][0-9]*:[1-9][0-9]*$")
        /** Field types a company offer (offer-schema.json `fields`) may use; the admin panel renders these. */
        val OFFER_FIELD_TYPES = setOf("text", "multiline", "url", "image", "select", "date")
        const val CATALOG_LOCK = "mobile/build-logic/catalog-keys.lock.json"
        /** offer-schema.json field flag: shown and editable in the admin panel only, never in any public payload. */
        const val ADMIN_ONLY = "adminOnly"
        /** Values the seed texts are filled with for the maxLength check (a long but realistic profile). */
        val SAMPLE_PLACEHOLDERS = mapOf(
            "name" to "Ramesh Kumar Sharma", "firstName" to "Ramesh",
            "city" to "Thiruvananthapuram", "designation" to "Insurance Advisor · Club Member",
        )
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
