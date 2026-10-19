package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitionsException
import com.sledje.agentpro.buildlogic.codegen.SiteSchemaKotlinGenerator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Companies and offers in shared/ and the codegen (docs/OFFERS.md, Phase 8b-1). */
class OffersTest {

    private fun assertViolation(violations: List<String>, vararg fragments: String) {
        assertTrue(violations.any { v -> fragments.all { it in v } }, "expected a violation containing ${fragments.toList()}, got:\n${violations.joinToString("\n")}")
    }

    @Test fun `the offers fixture passes every rule`() {
        assertEquals(emptyList(), Fixture().addOffers().verifier().verify())
    }

    @Test fun `offer-schema is optional and part of the catalog hash`() {
        val fx = Fixture()
        assertNull(fx.defs().offerSchema)
        val before = fx.defs().catalogHash
        fx.addOffers()
        assertNotNull(fx.defs().offerSchema)
        val withOffers = fx.defs().catalogHash
        assertNotEquals(before, withOffers)
        fx.replace("shared/offer-schema.json", "\"maxLength\": 60", "\"maxLength\": 70")
        assertNotEquals(withOffers, fx.defs().catalogHash)
    }

    @Test fun `catalog_sync emits the offer functions and the CHECKs of the lists present`() {
        val sql = CatalogSyncGenerator(Fixture().addOffers().defs()).sql()
        assertTrue("create or replace function public.offer_schema()" in sql)
        assertTrue("\"requiredIf\": { \"ctaKind\": \"link\" }" in sql, "offer-schema.json is embedded verbatim")
        assertTrue("public.catalog_offer_cta_kinds()\nreturns text[]" in sql)
        assertTrue("select array['enquire', 'link']::text[]" in sql)
        assertTrue("select array['enquire']::text[]" in sql, "agent kinds exclude the admin-only link")
        assertTrue("select array['not_found', 'other']::text[]" in sql)
        assertTrue("public.catalog_offer_limits()" in sql && "\"maxSectionItems\":6" in sql)
        assertTrue("revoke all on function public.offer_schema() from public, anon, authenticated;" in sql)
        assertTrue("alter table public.offers add constraint offers_cta_kind_check check (cta_kind in ('enquire', 'link'));" in sql)
        assertTrue("company_memberships_reject_reason_check check (reject_reason in ('not_found', 'other'))" in sql)
        assertFalse("offers_status_check" in sql, "no offerStatuses list in the fixture, so no CHECK")
        assertFalse("admins_role_check" in sql)
    }

    @Test fun `catalog_sync emits the locales and the default locale`() {
        val sql = CatalogSyncGenerator(Fixture().defs()).sql()
        assertTrue("public.catalog_locales()\nreturns text[]" in sql)
        assertTrue("public.catalog_default_locale()\nreturns text" in sql)
        assertFalse("offer_schema()" in sql, "nothing about offers without offer-schema.json")
    }

    @Test fun `site schema carries the offer types, rules and agent-only CTA kinds`() {
        val code = SiteSchemaKotlinGenerator(Fixture().addOffers().defs()).generate()
        assertTrue("type = FieldType.OfferRef," in code)
        assertTrue("type = FieldType.OfferList," in code)
        assertTrue("generated = \"uuid\"," in code)
        assertTrue("mustBeTrue = true," in code)
        assertTrue("maxDaysAhead = 366," in code)
        assertTrue("options = OfferCtaKind.active.filter { it.agentAllowed }.map { FieldOption(it.key, it.label, it.icon) }," in code, code)
        assertTrue("options = Interest.active.map {" in code, "lists without agentAllowed aren't filtered")
    }

    @Test fun `catalog gets the offer enums and limits`() {
        val code = CatalogKotlinGenerator(Fixture().addOffers().defs()).generate()
        assertTrue("enum class OfferCtaKind(" in code && "val agentAllowed: Boolean," in code)
        assertTrue("enum class MembershipRejectReason(" in code)
        assertTrue("object Offers {" in code && "const val imageAspect: String = \"16:9\"" in code)
    }

    // ---- verifyShared ---------------------------------------------------------------------------

    @Test fun `site-schema rule misuse fails`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/site-schema.json", "\"generated\": \"uuid\"", "\"generated\": \"serial\"")
        fx.replace("shared/site-schema.json", "\"equals\": true", "\"equals\": false")
        fx.replace("shared/site-schema.json", "\"maxDaysAhead\": 366", "\"maxDaysAhead\": 0")
        val v = fx.verifier().checkSchemaAndTemplates()
        assertViolation(v, "site-schema.offers.custom.id", "\"generated\" must be \"uuid\"")
        assertViolation(v, "site-schema.offers.custom.confirmed", "\"equals\" must be true")
        assertViolation(v, "site-schema.offers.custom.endsOn", "\"maxDaysAhead\"")
    }

    @Test fun `requiredIf and nested offer fields are refused in the site schema`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/site-schema.json", "{ \"key\": \"id\", \"type\": \"text\", \"generated\": \"uuid\", \"labelKey\": \"field.offer_id\" }",
            "{ \"key\": \"id\", \"type\": \"text\", \"generated\": \"uuid\", \"labelKey\": \"field.offer_id\" },\n" +
                "{ \"key\": \"ref\", \"type\": \"offerRef\", \"requiredIf\": { \"ctaKind\": \"link\" }, \"labelKey\": \"field.featured_offer\" }")
        val v = fx.verifier().checkSchemaAndTemplates()
        assertViolation(v, "site-schema.offers.custom.ref", "can't be inside a list")
        assertViolation(v, "site-schema.offers.custom.ref", "\"requiredIf\" is only for offer-schema.json")
    }

    @Test fun `limits are enforced against the site schema`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/catalog.json", "\"maxSectionItems\": 6", "\"maxSectionItems\": 4")
        fx.replace("shared/catalog.json", "\"maxValidityDays\": 366", "\"maxValidityDays\": 90")
        val v = fx.verifier().checkOffers()
        assertViolation(v, "site-schema.offers.items", "more than catalog.offers.maxSectionItems (4)")
        assertViolation(v, "site-schema.offers.custom.endsOn", "more than catalog.offers.maxValidityDays (90)")
    }

    @Test fun `bad limits and a missing agentAllowed fail`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/catalog.json", "\"imageAspect\": \"16:9\"", "\"imageAspect\": \"wide\"")
        fx.replace("shared/catalog.json", "\"order\": 2, \"agentAllowed\": false", "\"order\": 2")
        val v = fx.verifier().checkOffers()
        assertViolation(v, "catalog.offers.imageAspect")
        assertViolation(v, "catalog.offerCtaKinds.link", "\"agentAllowed\" must be true or false")
        assertViolation(v, "offer-schema.json.fields.image", "aspect must be catalog.offers.imageAspect")
    }

    @Test fun `offer-schema problems fail`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/offer-schema.json", "\"type\": \"url\"", "\"type\": \"offerRef\"")
        fx.replace("shared/offer-schema.json", "\"requiredIf\": { \"ctaKind\": \"link\" }", "\"requiredIf\": { \"ctaKind\": \"fax\" }")
        fx.replace("shared/offer-schema.json", "\"key\": \"title\", \"type\": \"text\"", "\"key\": \"title\", \"type\": \"image\"")
        val v = fx.verifier().checkOffers()
        assertViolation(v, "offer-schema.json.fields.ctaUrl", "unknown field type \"offerRef\"")
        assertViolation(v, "offer-schema.json.fields.ctaUrl", "\"fax\" is not a key of catalog.offerCtaKinds")
        assertViolation(v, "offer-schema.json.localized.title", "must be text or multiline")
    }

    @Test fun `requiredIf must name another field`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/offer-schema.json", "\"requiredIf\": { \"ctaKind\": \"link\" }", "\"requiredIf\": { \"kind\": \"link\" }")
        assertViolation(fx.verifier().checkOffers(), "offer-schema.json.fields.ctaUrl", "unknown field \"kind\"")
    }

    @Test fun `offer-schema labelKeys must be translated`() {
        val fx = Fixture().addOffers()
        fx.replace("shared/i18n/hi.json", "\"offer_field.cta_url\"", "\"offer_field.cta_url_x\"")
        assertViolation(fx.verifier().checkI18n(), "offer-schema.json.fields.ctaUrl", "\"offer_field.cta_url\"", "missing from i18n/hi.json")
    }

    @Test fun `invalid offer-schema json is reported by name`() {
        val fx = Fixture().addOffers()
        fx.file("shared/offer-schema.json").writeText("{ nope")
        val e = runCatching { fx.defs() }.exceptionOrNull()
        assertTrue(e is SharedDefinitionsException && e.problems.any { "shared/offer-schema.json is not valid JSON" in it }, "$e")
    }

    @Test fun `the real shared offers pass the offer rules and generate`() {
        val dir = realSharedDir() ?: return
        val defs = try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return }
        if (defs.offerSchema == null) return
        val code = CatalogKotlinGenerator(defs).generate()
        listOf("OfferStatus", "OfferPhase", "OfferCtaKind", "AdminRole", "MembershipStatus", "MembershipRejectReason")
            .forEach { assertTrue("enum class $it(" in code, it) }
        val sql = CatalogSyncGenerator(defs).sql()
        assertTrue("select array['enquire', 'call']::text[]" in sql, "agents may use enquire and call, never link")
        assertTrue("select 'en'::text" in sql)
        listOf("offers_status_check", "offers_cta_kind_check", "admins_role_check", "company_memberships_status_check",
            "company_memberships_reject_reason_check").forEach { assertTrue("add constraint $it " in sql, it) }
        assertEquals(File(dir, "offer-schema.json").readText().trim().length, defs.offerSchemaText!!.trim().length)
    }
}
