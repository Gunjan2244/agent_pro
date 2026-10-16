package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator.Plan
import com.sledje.agentpro.buildlogic.codegen.Migrations
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CatalogSyncGeneratorTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")

    private fun write(fx: Fixture, plan: Plan): File {
        assertIs<Plan.Write>(plan)
        return File(fx.migrations, plan.fileName).apply { writeText(plan.content) }
    }

    @Test fun `skipped while there is no migrations dir`() {
        val fx = Fixture()
        assertIs<Plan.Skipped>(CatalogSyncGenerator(fx.defs()) { now }.plan(fx.migrations))
    }

    @Test fun `skipped while only catalog_sync files exist`() {
        val fx = Fixture()
        fx.migrations.mkdirs()
        File(fx.migrations, "20261001000000_catalog_sync.sql").writeText("-- old\n")
        assertIs<Plan.Skipped>(CatalogSyncGenerator(fx.defs()) { now }.plan(fx.migrations))
    }

    @Test fun `written once a schema migration exists, then up to date`() {
        val fx = Fixture()
        fx.addSchemaMigration()
        val gen = CatalogSyncGenerator(fx.defs()) { now }
        val plan = gen.plan(fx.migrations)
        assertIs<Plan.Write>(plan)
        assertEquals("20261015120000_catalog_sync.sql", plan.fileName)
        val file = write(fx, plan)
        assertEquals(fx.defs().catalogHash, Migrations.readHash(file))
        assertIs<Plan.UpToDate>(CatalogSyncGenerator(fx.defs()) { now.plusSeconds(60) }.plan(fx.migrations))
    }

    @Test fun `a catalog or schema change produces a new file with a new hash`() {
        val fx = Fixture()
        fx.addSchemaMigration()
        val first = write(fx, CatalogSyncGenerator(fx.defs()) { now }.plan(fx.migrations))

        fx.replace("shared/site-schema.json", "\"maxLength\": 70", "\"maxLength\": 80")
        val second = write(fx, CatalogSyncGenerator(fx.defs()) { now.plusSeconds(60) }.plan(fx.migrations))
        assertNotEquals(Migrations.readHash(first), Migrations.readHash(second))
        assertEquals(second, Migrations.latestCatalogSync(fx.migrations))

        fx.replace("shared/catalog.json", "\"months\": 6", "\"months\": 3")
        assertIs<Plan.Write>(CatalogSyncGenerator(fx.defs()) { now.plusSeconds(120) }.plan(fx.migrations))
    }

    @Test fun `whitespace-only edits don't change the hash`() {
        val fx = Fixture()
        val before = fx.defs().catalogHash
        val f = fx.file("shared/catalog.json")
        f.writeText(f.readText().replace("\n", "\n\n  "))
        assertEquals(before, fx.defs().catalogHash)
    }

    @Test fun `timestamp sorts after the newest existing migration`() {
        val fx = Fixture()
        fx.addSchemaMigration("20991231235959_future.sql")
        val plan = CatalogSyncGenerator(fx.defs()) { now }.plan(fx.migrations)
        assertIs<Plan.Write>(plan)
        assertEquals("21000101000000_catalog_sync.sql", plan.fileName)
        assertTrue(plan.fileName > "20991231235959_future.sql")
    }

    @Test fun `sql follows the catalog_sync contract`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json",
            "{ \"key\": \"money_back\", \"labelKey\": \"interest.money_back\", \"icon\": \"payments\", \"tone\": \"neutral\", \"order\": 2 }",
            "{ \"key\": \"money_back\", \"labelKey\": \"interest.money_back\", \"icon\": \"payments\", \"tone\": \"neutral\", \"order\": 2, \"deprecated\": true }")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        assertTrue(sql.lines()[2].startsWith("-- catalog-hash: sha256:"), sql.lines()[2])
        listOf(
            "alter table public.enquiries drop constraint if exists enquiries_status_check;",
            "alter table public.enquiries add constraint enquiries_status_check check (status in ('new', 'follow_up', 'converted'));",
            // Deprecated keys stay valid in CHECK constraints...
            "alter table public.enquiries add constraint enquiries_interest_check check (interest in ('term', 'money_back'));",
            "alter table public.enquiries add constraint enquiries_source_check check (source in ('website'));",
            "alter table public.policies add constraint policies_premium_mode_check check (premium_mode in ('yearly', 'half_yearly'));",
            "alter table public.policies add constraint policies_status_check check (status in ('active'));",
            "alter table public.site_domains add constraint site_domains_status_check check (status in ('pending_dns', 'active'));",
            // ...but can't be submitted through the website.
            "create or replace function public.catalog_interest_keys()\nreturns text[]\nlanguage sql\nimmutable\nset search_path = ''\nas \$\$\n  select array['term']::text[]\n\$\$;",
            "revoke all on function public.catalog_interest_keys() from public, anon, authenticated;",
            "create or replace function public.catalog_premium_mode_months(p_mode text)\nreturns integer",
            "select case p_mode when 'yearly' then 12 when 'half_yearly' then 6 else null end",
            "revoke all on function public.catalog_premium_mode_months(text) from public, anon, authenticated;",
            "create or replace function public.site_schema()\nreturns jsonb",
            "select \$site_schema\${",
            "}\$site_schema\$::jsonb",
            "revoke all on function public.site_schema() from public, anon, authenticated;",
            "insert into public.app_config (key, value) values",
            "('flags', '{\"siteBuilder\":true,\"customDomains\":false}'::jsonb),",
            "('pricing', '{\"service\":{\"purchaseInr\":499,\"taxInclusive\":true,\"reminderDays\":[30,7,1]},\"domain\":{\"source\":\"registrar_live\",\"allowedTlds\":[\".in\",\".com\"]}}'::jsonb)",
            "on conflict (key) do update set value = excluded.value;",
        ).forEach { assertTrue(it in sql, "missing:\n$it\n--- in ---\n$sql") }
        assertTrue(fx.defs().siteSchemaText.trim() in sql, "site-schema.json is embedded verbatim")
    }

    @Test fun `the hash covers templates index json`() {
        val fx = Fixture()
        fx.addSchemaMigration()
        val first = write(fx, CatalogSyncGenerator(fx.defs()) { now }.plan(fx.migrations))
        fx.replace("shared/templates/index.json", "\"defaultAccent\": \"indigo\"", "\"defaultAccent\": \"slate\"")
        val plan = CatalogSyncGenerator(fx.defs()) { now.plusSeconds(60) }.plan(fx.migrations)
        assertIs<Plan.Write>(plan)
        assertNotEquals(Migrations.readHash(first), fx.defs().catalogHash)
        assertTrue("from shared/catalog.json,\n-- shared/site-schema.json and shared/templates/index.json." in plan.content)
    }

    @Test fun `site and profile columns get CHECKs from templates, swatches and locales`() {
        val fx = Fixture()
        fx.replace("shared/templates/index.json", "] }\n", ",\n  { \"key\": \"modern\", \"nameKey\": \"template.classic.name\", \"descriptionKey\": \"template.classic.description\", \"sections\": [\"hero\"], \"defaultAccent\": \"slate\", \"order\": 2 }\n] }\n")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        listOf(
            "alter table public.sites drop constraint if exists sites_template_key_check;",
            "alter table public.sites add constraint sites_template_key_check check (template_key in ('classic', 'modern'));",
            "alter table public.sites drop constraint if exists sites_accent_check;",
            "alter table public.sites add constraint sites_accent_check check (accent in ('indigo', 'slate'));",
            "alter table public.sites drop constraint if exists sites_locale_check;",
            "alter table public.sites add constraint sites_locale_check check (locale in ('en', 'hi'));",
            "alter table public.profiles drop constraint if exists profiles_preferred_language_check;",
            "alter table public.profiles add constraint profiles_preferred_language_check check (preferred_language in ('en', 'hi'));",
        ).forEach { assertTrue(it in sql, "missing:\n$it\n--- in ---\n$sql") }
        assertEquals(10, Regex("add constraint \\w+_check check").findAll(sql).count(), "10 catalog CHECK constraints")
    }

    @Test fun `deprecated locales and swatches stay valid in CHECKs`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"labelKey\": \"locale.hi\", \"order\": 2 }", "\"labelKey\": \"locale.hi\", \"order\": 2, \"deprecated\": true }")
        fx.replace("shared/catalog.json", "\"hex\": \"#37474F\", \"order\": 2 }", "\"hex\": \"#37474F\", \"order\": 2, \"deprecated\": true }")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        assertTrue("check (locale in ('en', 'hi'))" in sql && "check (preferred_language in ('en', 'hi'))" in sql)
        assertTrue("check (accent in ('indigo', 'slate'))" in sql)
    }

    @Test fun `interest keys - active only, and an _all variant with deprecated keys`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json",
            "\"icon\": \"payments\", \"tone\": \"neutral\", \"order\": 2 }",
            "\"icon\": \"payments\", \"tone\": \"neutral\", \"order\": 2, \"deprecated\": true }")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        assertTrue("create or replace function public.catalog_interest_keys()\nreturns text[]\nlanguage sql\nimmutable\nset search_path = ''\nas \$\$\n  select array['term']::text[]\n\$\$;" in sql, sql)
        assertTrue("create or replace function public.catalog_interest_keys_all()\nreturns text[]\nlanguage sql\nimmutable\nset search_path = ''\nas \$\$\n  select array['term', 'money_back']::text[]\n\$\$;" in sql, sql)
        assertTrue("revoke all on function public.catalog_interest_keys_all() from public, anon, authenticated;" in sql)
        assertTrue("check (interest in ('term', 'money_back'))" in sql, "the CHECK keeps the deprecated key")
    }

    @Test fun `reserved slugs become catalog_reserved_slugs()`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"reservedSlugs\": [\"admin\", \"agent-pro\"]", "\"reservedSlugs\": [\"admin\", \"agent-pro\", \"o'neil\"]")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        listOf(
            "-- Slugs no agent can use (catalog.reservedSlugs); used by private.is_reserved_slug.",
            "create or replace function public.catalog_reserved_slugs()\nreturns text[]\nlanguage sql\nimmutable\nset search_path = ''\nas \$\$\n  select array['admin', 'agent-pro', 'o''neil']::text[]\n\$\$;",
            "revoke all on function public.catalog_reserved_slugs() from public, anon, authenticated;",
        ).forEach { assertTrue(it in sql, "missing:\n$it\n--- in ---\n$sql") }
    }

    @Test fun `missing reservedSlugs or locales can't be generated`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "  \"reservedSlugs\": [\"admin\", \"agent-pro\"],\n", "")
        assertFailsWith<IllegalArgumentException> { CatalogSyncGenerator(fx.defs()) { now }.sql() }
        val fx2 = Fixture()
        fx2.replace("shared/catalog.json", "\"locales\": [", "\"localesRenamed\": [")
        assertFailsWith<IllegalArgumentException> { CatalogSyncGenerator(fx2.defs()) { now }.sql() }
    }

    @Test fun `single quotes are doubled and dollar tags never collide`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"source\": \"registrar_live\"", "\"source\": \"it's \$site_schema\$ \$\$\"")
        fx.replace("shared/site-schema.json", "\"aspect\": \"1:1\"", "\"aspect\": \"\$site_schema\$ and \$\$\"")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        assertTrue("it''s" in sql)
        assertTrue("select \$site_schema1\${" in sql, "inner tag avoids the content")
        assertTrue("as \$fn1\$\n  select \$site_schema1\$" in sql, "outer tag avoids the body")
        assertFalse("as \$\$\n  select \$site_schema1" in sql)
    }
}
