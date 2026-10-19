package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.codegen.Naming
import com.sledje.agentpro.buildlogic.codegen.PublicText
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitionsException
import com.sledje.agentpro.buildlogic.codegen.SiteSeedJson
import com.sledje.agentpro.buildlogic.codegen.SiteSeedKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.TemplatesKotlinGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Template families, the public text blocklist and the site seed (DECISIONS #76–#78). */
class SeedTest {

    private fun assertViolation(violations: List<String>, vararg fragments: String) {
        assertTrue(violations.any { v -> fragments.all { it in v } }, "expected a violation containing ${fragments.toList()}, got:\n${violations.joinToString("\n")}")
    }

    private fun full() = Fixture().addFamilies().addPublicText().addSeed()

    @Test fun `the families, blocklist and seed fixture passes every rule`() {
        assertEquals(emptyList(), full().verifier().verify())
        assertEquals(emptyList(), Fixture().addOffers().verifier().verify(), "adminOnly: true is accepted")
    }

    // ---- template families ----------------------------------------------------------------------

    @Test fun `templates carry their family when the catalog has templateFamilies`() {
        val code = TemplatesKotlinGenerator(Fixture().addFamilies().defs()).generate()
        assertTrue("val family: TemplateFamily," in code, code)
        assertTrue("family = TemplateFamily.Personal," in code && "family = TemplateFamily.Offers," in code)
        assertTrue("fun inFamily(family: TemplateFamily): List<TemplateSpec> = all.filter { it.family == family }" in code)
        assertFalse("family" in TemplatesKotlinGenerator(Fixture().defs()).generate(), "no families, no family field")
    }

    @Test fun `templateFamilies becomes an enum with a description resource`() {
        val code = CatalogKotlinGenerator(Fixture().addFamilies().defs()).generate()
        assertTrue("enum class TemplateFamily(" in code && "val description: StringResource," in code, code)
        assertTrue("Personal(\"personal\", Res.string.cat_template_family_personal_name, Res.drawable.ic_person, 1, " +
            "description = Res.string.cat_template_family_personal_description)," in code, code)
        assertEquals("TemplateFamily", Naming.catalogTypeName("templateFamilies"))
    }

    @Test fun `family problems fail`() {
        val fx = Fixture().addFamilies()
        fx.replace("shared/templates/index.json", "\"family\": \"offers\"", "\"family\": \"promo\"")
        val v = fx.verifier().checkTemplateFamilies()
        assertViolation(v, "templates/index.json.spotlight", "unknown family \"promo\"")
        assertViolation(v, "catalog.templateFamilies.offers", "no template has this family")
        fx.replace("shared/templates/index.json", "\"family\": \"personal\", ", "")
        assertViolation(fx.verifier().checkTemplateFamilies(), "templates/index.json.classic", "\"family\" is missing")
    }

    @Test fun `a family without templateFamilies fails`() {
        val fx = Fixture()
        fx.replace("shared/templates/index.json", "\"key\": \"classic\",", "\"key\": \"classic\", \"family\": \"personal\",")
        assertViolation(fx.verifier().checkTemplateFamilies(), "templates/index.json.classic", "needs catalog.templateFamilies")
    }

    @Test fun `family descriptionKeys must be translated`() {
        val fx = Fixture().addFamilies()
        fx.replace("shared/i18n/hi.json", "\"template_family.offers.description\"", "\"template_family.offers.description_x\"")
        assertViolation(fx.verifier().checkI18n(), "catalog.templateFamilies.offers.descriptionKey", "missing from i18n/hi.json")
    }

    // ---- interest images -----------------------------------------------------------------------------

    @Test fun `interest images are base names and become a field`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"icon\": \"shield\", \"tone\": \"neutral\", \"order\": 1 }", "\"icon\": \"shield\", \"tone\": \"neutral\", \"order\": 1, \"image\": \"interest-term\" }")
        fx.replace("shared/catalog.json", "\"icon\": \"payments\", \"tone\": \"neutral\", \"order\": 2 }", "\"icon\": \"payments\", \"tone\": \"neutral\", \"order\": 2, \"image\": \"interest-money-back\" }")
        assertEquals(emptyList(), fx.verifier().verify())
        val code = CatalogKotlinGenerator(fx.defs()).generate()
        assertTrue("val image: String," in code && "image = \"interest-term\")," in code, code)
        fx.replace("shared/catalog.json", "\"interest-money-back\"", "\"Interest_Money.webp\"")
        assertViolation(fx.verifier().checkRequiredStructure(), "catalog.interests.money_back", "\"image\" must be a file base name")
    }

    // ---- catalog_sync -----------------------------------------------------------------------------

    @Test fun `catalog_sync emits the family and blocked-term functions`() {
        val sql = CatalogSyncGenerator(Fixture().addFamilies().addPublicText().defs()).sql()
        assertTrue("create or replace function public.catalog_template_families()\nreturns text[]" in sql, sql)
        assertTrue("select array['personal', 'offers']::text[]" in sql)
        assertTrue("select case p_template_key when 'classic' then 'personal' when 'spotlight' then 'offers' else null end" in sql)
        assertTrue("revoke all on function public.catalog_template_family(text) from public, anon, authenticated;" in sql)
        assertTrue("create or replace function public.catalog_blocked_terms()\nreturns text[]\nlanguage sql\nimmutable" in sql)
        assertTrue("select array['jeevan anand', 'lic', 'एलआईसी']::text[]" in sql)
        assertTrue("revoke all on function public.catalog_blocked_terms() from public, anon, authenticated;" in sql)
        val plain = CatalogSyncGenerator(Fixture().defs()).sql()
        assertFalse("catalog_template_families" in plain || "catalog_blocked_terms" in plain, "only when the catalog has them")
    }

    @Test fun `the seed is not part of the catalog hash`() {
        val fx = Fixture().addFamilies()
        val before = fx.defs().catalogHash
        fx.addSeed()
        assertEquals(before, fx.defs().catalogHash)
    }

    // ---- public text ------------------------------------------------------------------------------

    @Test fun `blocked terms match word-bounded and case-insensitively`() {
        val terms = listOf("jeevan anand", "lic", "एलआईसी", "जीवन आनंद")
        assertEquals(listOf("jeevan anand"), PublicText.violations("Ask me about JEEVAN  Anand!", terms))
        assertEquals(listOf("lic"), PublicText.violations("An LIC-approved plan", terms))
        assertEquals(emptyList(), PublicText.violations("public policy, licence, relic", terms))
        assertEquals(listOf("एलआईसी"), PublicText.violations("मैं एलआईसी का एजेंट हूँ", terms))
        assertEquals(emptyList(), PublicText.violations("जीवन आनंदमय हो", terms), "a vowel sign or letter after the term means another word")
        assertEquals(listOf("जीवन आनंद"), PublicText.violations("जीवन आनंद योजना", terms))
        assertEquals(emptyList(), PublicText.violations("जीवन बीमा और बीमा सलाह", terms), "generic words alone never match")
    }

    @Test fun `bad blocked terms fail`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "  \"pricing\": {",
            "  \"publicTextRules\": { \"blockedTerms\": [\"Tech Term\", \"lic\", \"lic\", \"jeevan\", \" sbi life\", \"\"] },\n  \"pricing\": {")
        val v = fx.verifier().checkPublicText()
        assertViolation(v, "blockedTerms[0]", "must be lowercase")
        assertViolation(v, "duplicated term \"lic\"")
        assertViolation(v, "blockedTerms[3]", "generic everyday word")
        assertViolation(v, "blockedTerms[4]", "extra spaces")
        assertViolation(v, "blockedTerms[5]", "is empty")
    }

    @Test fun `non-NFC and missing blocked terms fail`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "  \"pricing\": {", "  \"publicTextRules\": { \"blockedTerms\": [\"jeevan a\u095Bad\"] },\n  \"pricing\": {")
        assertViolation(fx.verifier().checkPublicText(), "must be NFC-normalized")
        val empty = Fixture()
        empty.replace("shared/catalog.json", "  \"pricing\": {", "  \"publicTextRules\": { \"blockedTerms\": [] },\n  \"pricing\": {")
        assertViolation(empty.verifier().checkPublicText(), "catalog.publicTextRules", "non-empty \"blockedTerms\"")
    }

    @Test fun `site and seed texts may not contain a blocked term`() {
        val fx = full()
        fx.putI18n("site.offer_pick", "My LIC pick", "मेरी पसंद")
        fx.putI18n("seed.services.title", "Honest advice", "एलआईसी की सलाह")
        fx.putI18n("field.headline", "LIC headline", "शीर्षक")
        val v = fx.verifier().checkPublicText()
        assertViolation(v, "i18n/en.json", "\"site.offer_pick\"", "blocked term \"lic\"")
        assertViolation(v, "i18n/hi.json", "\"seed.services.title\"", "blocked term \"एलआईसी\"")
        assertFalse(v.any { "field.headline" in it }, "app labels aren't public site text")
    }

    // ---- offer-schema adminOnly --------------------------------------------------------------------

    @Test fun `adminOnly must be a boolean on a non-localized field`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/offer-schema.json", "\"adminOnly\": true", "\"adminOnly\": \"yes\"")
        fx.replace("shared/offer-schema.json", "\"maxLength\": 60, \"labelKey\": \"offer_field.title\"", "\"maxLength\": 60, \"adminOnly\": true, \"labelKey\": \"offer_field.title\"")
        val v = fx.verifier().checkOffers()
        assertViolation(v, "offer-schema.json.fields.internalName", "\"adminOnly\" must be true or false")
        assertViolation(v, "offer-schema.json.localized.title", "\"adminOnly\" is only for non-localized fields")
    }

    // ---- SiteSeed.kt ------------------------------------------------------------------------------

    @Test fun `SiteSeed resolves every text for every locale and keeps the value forms`() {
        val code = SiteSeedKotlinGenerator(full().defs()).generate()
        assertTrue("object SiteSeed {" in code && "const val DEFAULT_LOCALE: String = \"en\"" in code, code)
        assertTrue("val LOCALES: List<String> = listOf(\"en\", \"hi\")" in code)
        assertTrue("\"seed.hero.headline_1\" to SeedText(\"seed.hero.headline_1\", mapOf(\"en\" to \"Namaste, I'm {name} from {city}\", \"hi\" to \"नमस्ते, मैं {city} से {name} हूँ\"))," in code, code)
        assertTrue("\"headline\" to SeedValue.Variants(listOf(SeedVariant(texts.getValue(\"seed.hero.headline_1\"), texts.getValue(\"seed.hero.headline_1_short\")), SeedVariant(texts.getValue(\"seed.hero.headline_2\"))))," in code)
        assertTrue("\"yearsExperience\" to SeedValue.Literal(JsonPrimitive(5L))," in code)
        assertTrue("\"showAgencyCode\" to SeedValue.ByLocale(mapOf(\"en\" to JsonPrimitive(false), \"hi\" to JsonPrimitive(true)))," in code)
        assertTrue("\"offered\" to SeedValue.Literal(JsonArray(listOf(JsonPrimitive(\"term\"), JsonPrimitive(\"money_back\"))))," in code)
        assertTrue("\"items\" to SeedValue.Items(listOf(" in code && "\"icon\" to SeedValue.Literal(JsonPrimitive(\"shield\"))," in code)
        assertTrue("val hidden: List<String> = listOf(\"about\")" in code)
        assertTrue("val visibleOnlyInFamily: Map<String, String> = mapOf(\"services\" to \"offers\")" in code)
        assertTrue("val REQUIRED_PLACEHOLDERS: List<String> = listOf(\"name\", \"firstName\")" in code)
        // The pick is FNV-1a 32 over seed + ":" + salt, the same as the reference implementation.
        assertTrue("var hash = 2166136261u" in code && "hash *= 16777619u" in code && "fnv1a32(\"\$seed:\$salt\") % count.toUInt()" in code, code)
        assertFalse(Regex("(?m)[ \t]+$").containsMatchIn(code), "no trailing whitespace")
    }

    @Test fun `the variant pick is FNV-1a 32 with a per-field salt`() {
        assertEquals(0x811c9dc5L, SiteSeedJson.fnv1a32(""))
        assertEquals(0xe40c292cL, SiteSeedJson.fnv1a32("a"))
        assertEquals(0xbf9cf968L, SiteSeedJson.fnv1a32("foobar"))
        assertEquals(538106393L, SiteSeedJson.fnv1a32("नमस्ते"), "UTF-8 bytes")
        val agent = "00000000-0000-0000-0000-000000000001"
        assertEquals(3217482297L, SiteSeedJson.fnv1a32("$agent:about.bio"))
        assertEquals(listOf(1, 0, 0, 2), listOf("hero.headline", "hero.subheadline", "about.bio", "faq.items.2.answer").map { SiteSeedJson.pick(agent, it, 3) })
    }

    // ---- verifyShared: the seed -------------------------------------------------------------------

    @Test fun `seed text problems fail`() {
        val fx = full()
        fx.putI18n("seed.services.title", "A very long title that is clearly more than forty characters", "ईमानदार सलाह")
        fx.putI18n("seed.hero.headline_2", "Hello from {city}", "{city} से नमस्कार")
        fx.putI18n("seed.hero.headline_1_short", "Namaste, I'm {name} from {city}", "नमस्ते, मैं {city} से {name} हूँ")
        val v = fx.verifier().checkSeed()
        assertViolation(v, "site-seed.json.services.items[0].title", "\"seed.services.title\" (en)", "more than maxLength 40")
        assertViolation(v, "site-seed.json.hero.headline", "\"seed.hero.headline_2\" (en) uses {city}", "give it a \"fallback\"")
        assertViolation(v, "fallback of seed.hero.headline_1", "may only use {name}, {firstName}")
    }

    @Test fun `seed placeholders and keys are checked`() {
        val fx = full()
        fx.putI18n("seed.hero.headline_2", "Hello {nickname}", "नमस्कार {nickname}")
        fx.replace("shared/i18n/hi.json", "\"seed.services.title\"", "\"seed.services.title_x\"")
        fx.replace("shared/site-seed.json", "\"seed.services.title\"", "\"services.title\"")
        val v = fx.verifier().checkSeed()
        assertViolation(v, "\"seed.hero.headline_2\" (en) uses unknown placeholder {nickname}")
        assertViolation(v, "\"services.title\" must start with \"seed.\"")
        assertViolation(v, "i18n key \"services.title\" is missing from i18n/en.json")
    }

    @Test fun `seed values must be valid for the field`() {
        val fx = full()
        fx.replace("shared/site-seed.json", "{ \"value\": \"shield\" }", "{ \"value\": \"rocket\" }")
        fx.replace("shared/site-seed.json", "[\"term\", \"money_back\"]", "[\"term\", \"ulip\"]")
        fx.replace("shared/site-seed.json", "\"hi\": true", "\"hi\": \"yes\"")
        fx.replace("shared/site-seed.json", "{ \"value\": 5 }", "{ \"fromProfile\": \"age\" }")
        val v = fx.verifier().checkSeed()
        assertViolation(v, "site-seed.json.services.items[0].icon", "\"rocket\" is not an option")
        assertViolation(v, "site-seed.json.services.offered", "\"ulip\" is not an option")
        assertViolation(v, "site-seed.json.about.showAgencyCode.hi", "toggle value must be true or false")
        assertViolation(v, "site-seed.json.about.yearsExperience", "fromProfile \"age\" is not a profile field")
    }

    @Test fun `seed forms and structure are checked`() {
        val fx = full()
        fx.replace("shared/site-seed.json", "\"showAgencyCode\": { \"byLocale\": { \"en\": false, \"hi\": true } }",
            "\"showAgencyCode\": { \"byLocale\": { \"en\": false } }, \"bio\": { \"value\": \"x\" }")
        fx.replace("shared/site-seed.json", "\"seed.hero.headline_2\" ] }", "\"seed.hero.headline_2\" ] },\n      \"photo\": { \"variants\": [\"seed.services.title\"] }")
        fx.replace("shared/site-seed.json", "{ \"icon\": { \"value\": \"shield\" }, \"title\": { \"variants\": [\"seed.services.title\"] } }",
            "{ \"icon\": { \"value\": \"shield\" } }, { \"icon\": { \"value\": \"shield\", \"fromProfile\": \"city\" }, \"title\": { \"variants\": [\"seed.services.title\"] } }")
        val v = fx.verifier().checkSeed()
        assertViolation(v, "site-seed.json.about.showAgencyCode", "byLocale has no value for locale \"hi\"")
        assertViolation(v, "site-seed.json.about.bio", "unknown field of section about")
        assertViolation(v, "site-seed.json.hero.photo", "variants are only for text and multiline fields")
        assertViolation(v, "site-seed.json.services.items[0]", "required item field \"title\" is missing")
        assertViolation(v, "site-seed.json.services.items[1].icon", "must have exactly one of")
    }

    @Test fun `seed list size and visibility are checked`() {
        val fx = full()
        val item = "{ \"icon\": { \"value\": \"shield\" }, \"title\": { \"variants\": [\"seed.services.title\"] } }"
        fx.replace("shared/site-seed.json", item, List(5) { item }.joinToString(", "))
        fx.replace("shared/site-seed.json", "\"hidden\": [\"about\"]", "\"hidden\": [\"hero\", \"blog\"]")
        fx.replace("shared/site-seed.json", "{ \"services\": \"offers\" }", "{ \"services\": \"promo\" }")
        fx.replace("shared/site-seed.json", "\"requiredPlaceholders\": [\"name\", \"firstName\"]", "\"requiredPlaceholders\": [\"name\", \"firstName\", \"phone\"]")
        val v = fx.verifier().checkSeed()
        assertViolation(v, "site-seed.json.services.items", "5 items is more than maxItems (4)")
        assertViolation(v, "visibility.hidden", "\"hero\" is pinned")
        assertViolation(v, "visibility.hidden", "unknown section \"blog\"")
        assertViolation(v, "visibleOnlyInFamily.services", "unknown template family \"promo\"")
        assertViolation(v, "requiredPlaceholders", "\"phone\" is not in \"placeholders\"")
    }

    @Test fun `an unknown seed section fails`() {
        val fx = full()
        fx.replace("shared/site-seed.json", "\"sections\": {", "\"sections\": {\n    \"blog\": { \"title\": { \"value\": \"x\" } },")
        assertViolation(fx.verifier().checkSeed(), "site-seed.json.sections", "unknown section \"blog\"")
    }

    // ---- the real shared/ ----------------------------------------------------------------------------

    @Test fun `the real seed generates and passes the seed, family and blocklist rules`() {
        val dir = realSharedDir() ?: return
        val defs = try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return }
        if (defs.siteSeed == null) return
        val code = SiteSeedKotlinGenerator(defs).generate()
        SiteSeedJson.textKeys(defs.siteSeed!!).forEach { assertTrue("\"$it\" to SeedText(" in code, it) }
        val fx = Fixture()
        dir.copyRecursively(fx.shared, overwrite = true)
        val verifier = fx.verifier()
        assertEquals(emptyList(), verifier.checkSeed() + verifier.checkTemplateFamilies() + verifier.checkPublicText())
        val terms = PublicText.blockedTerms(defs)
        listOf("jeevan", "bima", "जीवन", "बीमा").forEach { assertFalse(it in terms, it) }
        assertTrue(PublicText.violations("Life Insurance Corporation of India", terms).isNotEmpty())
        assertTrue(PublicText.violations("जीवन बीमा और स्वास्थ्य बीमा", terms).isEmpty(), "\"life insurance\" in Hindi is generic")
    }
}
