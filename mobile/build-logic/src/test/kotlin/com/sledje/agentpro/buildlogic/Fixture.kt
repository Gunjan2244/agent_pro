package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.NoticeHashes
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.verify.SharedVerifier
import com.sledje.agentpro.buildlogic.verify.VerifyInputs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files

/**
 * A writable copy of src/test/resources/fixture (a small but complete, valid shared/ set plus
 * composeResources) in a temp dir. Tests mutate files to produce failing cases.
 */
class Fixture {
    val root: File = Files.createTempDirectory("agentpro-fixture").toFile().also { dir ->
        File(javaClass.getResource("/fixture")!!.toURI()).copyRecursively(dir)
    }
    val shared = File(root, "shared")
    val composeResources = File(root, "composeResources")
    val migrations = File(root, "supabase/migrations")
    val kotlinSrc = File(root, "src").also { it.mkdirs() }

    fun file(rel: String) = File(root, rel)

    /** Replaces exactly one occurrence of [old] in [rel]. */
    fun replace(rel: String, old: String, new: String) {
        val f = file(rel)
        val text = f.readText()
        require(text.split(old).size == 2) { "\"$old\" must occur exactly once in $rel" }
        f.writeText(text.replace(old, new))
    }

    fun defs(): SharedDefinitions = SharedDefinitions.load(shared)

    /** A lock that matches the current catalog exactly. */
    fun currentLock(): Map<String, List<String>> = CatalogKeysLock.merged(emptyMap(), defs())

    /** Notice hashes that match the current notices exactly. */
    fun currentNoticeLock(): NoticeHashes = CatalogKeysLock.mergedNoticeHashes(emptyMap(), defs())

    val privacyNoticesJson: File get() = File(shared, PrivacyNotices.WEB_JSON)

    fun verifier(lock: Map<String, List<String>> = currentLock(), noticeLock: NoticeHashes = currentNoticeLock()) = SharedVerifier(
        VerifyInputs(
            defs = defs(),
            composeResourcesDir = composeResources,
            kotlinSourceRoots = listOf(kotlinSrc),
            migrationsDir = migrations,
            keysLock = lock,
            displayRoot = root,
            noticeLock = noticeLock,
            privacyNoticesJson = privacyNoticesJson,
        )
    )

    /**
     * Adds a small privacy set to the fixture: two notices (one with an upper-case release placeholder),
     * dataRequestKinds, dataRequestStatuses and a retention object, plus their en/hi texts, and writes
     * shared/generated/privacy-notices.json. The result passes every verifyShared rule.
     */
    fun addPrivacy(): Fixture {
        replace("shared/catalog.json", "  \"pricing\": {", """
          |  "privacyNotices": [
          |    { "key": "agent_signup", "labelKey": "privacy.agent_signup.title", "order": 1, "version": 1,
          |      "textKeys": ["privacy.agent_signup.consent", "privacy.agent_signup.title", "privacy.agent_signup.grievance"] },
          |    { "key": "enquiry_form", "labelKey": "privacy.enquiry_form.title", "order": 2, "version": 2,
          |      "textKeys": ["privacy.enquiry_form.consent", "privacy.enquiry_form.title"] }
          |  ],
          |  "dataRequestKinds": [
          |    { "key": "access", "labelKey": "data_request_kind.access", "icon": "person", "tone": "neutral", "order": 1 },
          |    { "key": "erasure", "labelKey": "data_request_kind.erasure", "icon": "error", "tone": "neutral", "order": 2 }
          |  ],
          |  "dataRequestStatuses": [
          |    { "key": "open", "labelKey": "data_request_status.open", "icon": "hourglass_empty", "tone": "warning", "order": 1, "terminal": false },
          |    { "key": "done", "labelKey": "data_request_status.done", "icon": "check_circle", "tone": "success", "order": 2, "terminal": true }
          |  ],
          |  "retention": { "softDeletedDays": 30, "lapsedAccount": { "deleteAfterMonths": 12, "noticeDaysBefore": [30, 7, 2] } },
          |  "pricing": {""".trimMargin())
        val en = linkedMapOf(
            "privacy.agent_signup.consent" to "I'm 18 or older, and I agree to the Terms and the Privacy notice.",
            "privacy.agent_signup.title" to "Privacy notice",
            "privacy.agent_signup.grievance" to "Grievance officer: {GRIEVANCE_EMAIL}",
            "privacy.enquiry_form.consent" to "I agree that {name} may contact me.",
            "privacy.enquiry_form.title" to "How your data is used",
            "data_request_kind.access" to "See my data",
            "data_request_kind.erasure" to "Delete my data",
            "data_request_status.open" to "Open",
            "data_request_status.done" to "Done",
        )
        val hi = linkedMapOf(
            "privacy.agent_signup.consent" to "मेरी आयु 18 वर्ष या उससे अधिक है, और मैं शर्तों और गोपनीयता सूचना से सहमत हूँ।",
            "privacy.agent_signup.title" to "गोपनीयता सूचना",
            "privacy.agent_signup.grievance" to "शिकायत अधिकारी: {GRIEVANCE_EMAIL}",
            "privacy.enquiry_form.consent" to "मैं सहमत हूँ कि {name} मुझसे संपर्क कर सकते हैं।",
            "privacy.enquiry_form.title" to "आपके डेटा का उपयोग कैसे होगा",
            "data_request_kind.access" to "मेरा डेटा दिखाएँ",
            "data_request_kind.erasure" to "मेरा डेटा हटाएँ",
            "data_request_status.open" to "खुला",
            "data_request_status.done" to "पूरा हुआ",
        )
        fun append(rel: String, entries: Map<String, String>) {
            val f = file(rel)
            val body = entries.entries.joinToString("") { (k, v) -> ",\n  " + JsonPrimitive(k) + ": " + JsonPrimitive(v) }
            f.writeText(f.readText().trimEnd().removeSuffix("}").trimEnd() + body + "\n}\n")
        }
        append("shared/i18n/en.json", en)
        append("shared/i18n/hi.json", hi)
        writePrivacyJson()
        return this
    }

    /**
     * Adds a small offers set (docs/OFFERS.md): offerCtaKinds (one admin-only kind), membershipRejectReasons, the
     * `offers` limits, offer-schema.json, and an `offers` section with an offerList and custom offers, plus a
     * featuredOffer on the hero, with their en/hi labels. The result passes every verifyShared rule.
     */
    fun addOffers(): Fixture {
        replace("shared/catalog.json", "  \"pricing\": {", """
          |  "offerCtaKinds": [
          |    { "key": "enquire", "labelKey": "offer_cta_kind.enquire", "icon": "web", "tone": "neutral", "order": 1, "agentAllowed": true },
          |    { "key": "link", "labelKey": "offer_cta_kind.link", "icon": "public", "tone": "neutral", "order": 2, "agentAllowed": false }
          |  ],
          |  "membershipRejectReasons": [
          |    { "key": "not_found", "labelKey": "membership_reject_reason.not_found", "icon": "person", "tone": "neutral", "order": 1 },
          |    { "key": "other", "labelKey": "membership_reject_reason.other", "icon": "error", "tone": "neutral", "order": 2 }
          |  ],
          |  "offers": { "maxSectionItems": 6, "maxValidityDays": 366, "imageAspect": "16:9" },
          |  "pricing": {""".trimMargin())
        file("shared/offer-schema.json").writeText("""
          |{ "version": 1,
          |  "localized": [ { "key": "title", "type": "text", "required": true, "maxLength": 60, "labelKey": "offer_field.title" } ],
          |  "fields": [
          |    { "key": "internalName", "type": "text", "maxLength": 120, "adminOnly": true, "labelKey": "offer_field.internal_name" },
          |    { "key": "image", "type": "image", "aspect": "16:9", "labelKey": "offer_field.image" },
          |    { "key": "ctaKind", "type": "select", "required": true, "optionsFrom": "catalog.offerCtaKinds", "labelKey": "offer_field.cta_kind" },
          |    { "key": "ctaUrl", "type": "url", "requiredIf": { "ctaKind": "link" }, "labelKey": "offer_field.cta_url" },
          |    { "key": "endsAt", "type": "date", "required": true, "labelKey": "offer_field.ends_at" } ] }
          |""".trimMargin())
        replace("shared/site-schema.json", "\"defaultKey\": \"site.cta_default\" }",
            "\"defaultKey\": \"site.cta_default\" },\n        { \"key\": \"featuredOffer\", \"type\": \"offerRef\", \"labelKey\": \"field.featured_offer\" }")
        val sections = file("shared/site-schema.json").readText().trimEnd()
        file("shared/site-schema.json").writeText(sections.removeSuffix("}").trimEnd().removeSuffix("]").trimEnd() + """,
          |    { "key": "offers", "labelKey": "section.offers", "icon": "web", "defaultVisible": false,
          |      "fields": [
          |        { "key": "items", "type": "offerList", "maxItems": 6, "labelKey": "field.offers_items" },
          |        { "key": "custom", "type": "list", "maxItems": 6, "labelKey": "field.custom_offers",
          |          "itemFields": [
          |            { "key": "id", "type": "text", "generated": "uuid", "labelKey": "field.offer_id" },
          |            { "key": "endsOn", "type": "date", "required": true, "maxDaysAhead": 366, "labelKey": "offer_field.ends_at" },
          |            { "key": "ctaKind", "type": "select", "required": true, "optionsFrom": "catalog.offerCtaKinds", "labelKey": "offer_field.cta_kind" },
          |            { "key": "confirmed", "type": "toggle", "equals": true, "labelKey": "field.custom_offer_confirm" }
          |          ] }
          |      ] }
          |  ]
          |}
          |""".trimMargin())
        val keys = listOf(
            "offer_cta_kind.enquire", "offer_cta_kind.link", "membership_reject_reason.not_found", "membership_reject_reason.other",
            "offer_field.title", "offer_field.internal_name", "offer_field.image", "offer_field.cta_kind", "offer_field.cta_url", "offer_field.ends_at",
            "field.featured_offer", "section.offers", "field.offers_items", "field.custom_offers", "field.offer_id", "field.custom_offer_confirm",
        )
        appendI18n("shared/i18n/en.json", keys.associateWith { "EN $it" })
        appendI18n("shared/i18n/hi.json", keys.associateWith { "हिं $it" })
        return this
    }

    /**
     * Adds template families (DECISIONS #76): `catalog.templateFamilies` (personal, offers), `family` on `classic`
     * and a second template `spotlight` in the offers family, with their en/hi labels. Passes every verifyShared rule.
     */
    fun addFamilies(): Fixture {
        replace("shared/catalog.json", "  \"pricing\": {", """
          |  "templateFamilies": [
          |    { "key": "personal", "labelKey": "template_family.personal.name", "descriptionKey": "template_family.personal.description", "icon": "person", "order": 1 },
          |    { "key": "offers", "labelKey": "template_family.offers.name", "descriptionKey": "template_family.offers.description", "icon": "web", "order": 2 }
          |  ],
          |  "pricing": {""".trimMargin())
        file("shared/templates/index.json").writeText("""
          |{ "version": 1, "templates": [
          |  { "key": "classic", "family": "personal", "nameKey": "template.classic.name", "descriptionKey": "template.classic.description",
          |    "sections": ["hero", "about", "services"], "defaultAccent": "indigo", "preview": "web/templates/classic/preview.webp", "order": 1 },
          |  { "key": "spotlight", "family": "offers", "nameKey": "template.spotlight.name", "descriptionKey": "template.spotlight.description",
          |    "sections": ["hero", "services", "about"], "defaultAccent": "slate", "preview": "web/templates/spotlight/preview.webp", "order": 2 }
          |] }
          |""".trimMargin())
        val keys = listOf("template_family.personal.name", "template_family.personal.description", "template_family.offers.name",
            "template_family.offers.description", "template.spotlight.name", "template.spotlight.description")
        appendI18n("shared/i18n/en.json", keys.associateWith { "EN $it" })
        appendI18n("shared/i18n/hi.json", keys.associateWith { "हिं $it" })
        return this
    }

    /** Adds `catalog.publicTextRules` (DECISIONS #77) with a Latin product, an insurer and a Devanagari term. Passes every rule. */
    fun addPublicText(): Fixture {
        replace("shared/catalog.json", "  \"pricing\": {", """
          |  "publicTextRules": { "blockedTerms": ["jeevan anand", "lic", "एलआईसी"] },
          |  "pricing": {""".trimMargin())
        return this
    }

    /**
     * Adds shared/site-seed.json (DECISIONS #78) using every value form, with its en/hi texts. Needs [addFamilies]
     * (visibleOnlyInFamily names the offers family). Passes every rule.
     */
    fun addSeed(): Fixture {
        file("shared/site-seed.json").writeText("""
          |{ "version": 1,
          |  "placeholders": { "name": "fullName", "firstName": "firstName", "city": "city" },
          |  "requiredPlaceholders": ["name", "firstName"],
          |  "visibility": { "hidden": ["about"], "visibleOnlyInFamily": { "services": "offers" } },
          |  "sections": {
          |    "hero": {
          |      "headline": { "variants": [ { "key": "seed.hero.headline_1", "fallback": "seed.hero.headline_1_short" }, "seed.hero.headline_2" ] }
          |    },
          |    "about": {
          |      "yearsExperience": { "value": 5 },
          |      "showAgencyCode": { "byLocale": { "en": false, "hi": true } }
          |    },
          |    "services": {
          |      "offered": { "value": ["term", "money_back"] },
          |      "items": { "items": [ { "icon": { "value": "shield" }, "title": { "variants": ["seed.services.title"] } } ] }
          |    }
          |  }
          |}
          |""".trimMargin())
        appendI18n("shared/i18n/en.json", linkedMapOf(
            "seed.hero.headline_1" to "Namaste, I'm {name} from {city}",
            "seed.hero.headline_1_short" to "Namaste, I'm {name}",
            "seed.hero.headline_2" to "Hello, I'm {firstName}",
            "seed.services.title" to "Honest advice",
        ))
        appendI18n("shared/i18n/hi.json", linkedMapOf(
            "seed.hero.headline_1" to "नमस्ते, मैं {city} से {name} हूँ",
            "seed.hero.headline_1_short" to "नमस्ते, मैं {name} हूँ",
            "seed.hero.headline_2" to "नमस्कार, मैं {firstName} हूँ",
            "seed.services.title" to "ईमानदार सलाह",
        ))
        return this
    }

    /** Adds (or replaces) one i18n key in both locales. */
    fun putI18n(key: String, en: String, hi: String) {
        for ((rel, value) in listOf("shared/i18n/en.json" to en, "shared/i18n/hi.json" to hi)) {
            val f = file(rel)
            val obj = Json.parseToJsonElement(f.readText()).jsonObject
            f.writeText(JsonObject(obj + (key to JsonPrimitive(value))).toString())
        }
    }

    private fun appendI18n(rel: String, entries: Map<String, String>) {
        val f = file(rel)
        val body = entries.entries.joinToString("") { (k, v) -> ",\n  " + JsonPrimitive(k) + ": " + JsonPrimitive(v) }
        f.writeText(f.readText().trimEnd().removeSuffix("}").trimEnd() + body + "\n}\n")
    }

    fun writePrivacyJson() = privacyNoticesJson.apply { parentFile.mkdirs(); writeText(PrivacyNotices(defs()).webJson()) }

    fun writeKotlin(rel: String, code: String) = File(kotlinSrc, rel).apply { parentFile.mkdirs(); writeText(code) }

    fun addSchemaMigration(name: String = "20261015100000_schema.sql") =
        File(migrations, name).apply { parentFile.mkdirs(); writeText("create table public.enquiries (id uuid);\n") }
}

/** The real repo's shared/ (tests that use it are skipped when it isn't there). */
fun realSharedDir(): File? {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "shared/catalog.json")
        if (candidate.isFile && File(dir, "mobile").isDirectory) return candidate.parentFile
        dir = dir.parentFile
    }
    return null
}
