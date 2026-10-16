package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.verify.Contrast
import com.sledje.agentpro.buildlogic.verify.KotlinSpacingScanner
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each verifyShared rule has a passing fixture (the unmodified one) and a failing one. */
class SharedVerifierTest {

    private fun assertViolation(violations: List<String>, vararg fragments: String) {
        assertTrue(violations.any { v -> fragments.all { it in v } }, "expected a violation containing ${fragments.toList()}, got:\n${violations.joinToString("\n")}")
    }

    @Test fun `the valid fixture passes every rule`() {
        assertEquals(emptyList(), Fixture().verifier().verify())
    }

    // ---- i18n -----------------------------------------------------------------------------------

    @Test fun `missing hi labelKey fails`() {
        val fx = Fixture()
        fx.replace("shared/i18n/hi.json", "\"enquiry_status.follow_up\"", "\"enquiry_status.follow_up_typo\"")
        val v = fx.verifier().checkI18n()
        assertViolation(v, "catalog.enquiryStatuses.follow_up", "\"enquiry_status.follow_up\"", "missing from i18n/hi.json")
        assertViolation(v, "\"enquiry_status.follow_up\" is in en.json but missing from hi.json")
    }

    @Test fun `missing en labelKey fails`() {
        val fx = Fixture()
        fx.replace("shared/i18n/en.json", "\"field.headline\"", "\"field.headline_x\"")
        assertViolation(fx.verifier().checkI18n(), "site-schema.hero.headline", "missing from i18n/en.json")
    }

    @Test fun `different placeholders fail`() {
        val fx = Fixture()
        fx.replace("shared/i18n/hi.json", "{name} जल्द", "{naam} जल्द")
        assertViolation(fx.verifier().checkI18n(), "site.form_thank_you", "placeholders differ")
    }

    // ---- tones and icons ------------------------------------------------------------------------

    @Test fun `unknown tone fails`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"tone\": \"warning\"", "\"tone\": \"amber\"")
        assertViolation(fx.verifier().checkTonesAndIcons(), "enquiryStatuses.follow_up", "unknown tone \"amber\"")
    }

    @Test fun `unknown catalog icon fails`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"icon\": \"fiber_new\"", "\"icon\": \"no_such_icon\"")
        assertViolation(fx.verifier().checkTonesAndIcons(), "enquiryStatuses.new", "unknown icon \"no_such_icon\"", "ic_no_such_icon.xml")
    }

    @Test fun `missing drawable for a schema icon fails`() {
        val fx = Fixture()
        File(fx.composeResources, "drawable/ic_badge.xml").delete()
        assertViolation(fx.verifier().checkTonesAndIcons(), "site-schema.hero", "unknown icon \"badge\"")
    }

    // ---- keys -----------------------------------------------------------------------------------

    @Test fun `duplicated key fails`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "{ \"key\": \"converted\"", "{ \"key\": \"new\"")
        assertViolation(fx.verifier().checkKeys(), "catalog.enquiryStatuses", "duplicated key \"new\"")
    }

    @Test fun `removed key fails, deprecated key passes`() {
        val fx = Fixture()
        val lock = fx.currentLock().toMutableMap()
        lock["interests"] = lock.getValue("interests") + "child"
        assertViolation(fx.verifier(lock).checkKeys(), "catalog.interests", "key \"child\" was removed", "deprecated")

        val fx2 = Fixture()
        val lock2 = fx2.currentLock()
        fx2.replace("shared/catalog.json", "\"order\": 2, \"terminal\": false }", "\"order\": 2, \"terminal\": false, \"deprecated\": true }")
        assertEquals(emptyList(), fx2.verifier(lock2).checkKeys())
    }

    @Test fun `key not yet in the lockfile fails`() {
        val fx = Fixture()
        val lock = fx.currentLock().mapValues { (k, v) -> if (k == "interests") v - "money_back" else v }
        assertViolation(fx.verifier(lock).checkKeys(), "\"money_back\" isn't in", "generateShared")
        assertEquals(emptyList(), fx.verifier(CatalogKeysLock.merged(lock, fx.defs())).checkKeys())
    }

    @Test fun `duplicated schema field fails`() {
        val fx = Fixture()
        fx.replace("shared/site-schema.json", "{ \"key\": \"photo\"", "{ \"key\": \"headline\"")
        assertViolation(fx.verifier().checkKeys(), "site-schema.hero", "duplicated field \"headline\"")
    }

    // ---- catalog_sync ---------------------------------------------------------------------------

    @Test fun `catalog_sync check is skipped without schema migrations`() {
        val fx = Fixture()
        fx.migrations.mkdirs()
        assertEquals(emptyList(), fx.verifier().checkCatalogSync())
    }

    @Test fun `schema migrations without catalog_sync fail`() {
        val fx = Fixture()
        fx.addSchemaMigration()
        assertViolation(fx.verifier().checkCatalogSync(), "no *_catalog_sync.sql", "generateShared")
    }

    @Test fun `catalog_sync hash mismatch fails, matching hash passes`() {
        val fx = Fixture()
        fx.addSchemaMigration()
        val plan = CatalogSyncGenerator(fx.defs()) { Instant.parse("2026-10-15T12:00:00Z") }.plan(fx.migrations)
        plan as CatalogSyncGenerator.Plan.Write
        File(fx.migrations, plan.fileName).writeText(plan.content)
        assertEquals(emptyList(), fx.verifier().checkCatalogSync())

        fx.replace("shared/catalog.json", "\"months\": 6", "\"months\": 3")
        assertViolation(fx.verifier().checkCatalogSync(), "20261015120000_catalog_sync.sql", "doesn't match")
    }

    // ---- catalog extras -------------------------------------------------------------------------

    @Test fun `invalid or duplicated reserved slugs fail`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"reservedSlugs\": [\"admin\", \"agent-pro\"]", "\"reservedSlugs\": [\"Admin\", \"agent-pro\", \"agent-pro\", 3]")
        val v = fx.verifier().checkRequiredStructure()
        assertViolation(v, "catalog.reservedSlugs[0]", "lowercase slug")
        assertViolation(v, "catalog.reservedSlugs[3]")
        assertViolation(v, "catalog.reservedSlugs: duplicated slug \"agent-pro\"")
    }

    @Test fun `missing reservedSlugs, locales or templates fail`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "  \"reservedSlugs\": [\"admin\", \"agent-pro\"],\n", "")
        fx.replace("shared/catalog.json", "\"locales\": [", "\"localez\": [")
        fx.file("shared/templates/index.json").writeText("{ \"version\": 1, \"templates\": [] }\n")
        val v = fx.verifier().checkRequiredStructure()
        assertViolation(v, "\"reservedSlugs\" must be an array of strings")
        assertViolation(v, "list \"locales\" is missing")
        assertViolation(v, "templates/index.json: \"templates\" must be a non-empty array")
    }

    @Test fun `locales need no icon but still need labels`() {
        val fx = Fixture()
        assertEquals(emptyList(), fx.verifier().checkTonesAndIcons())
        fx.replace("shared/i18n/hi.json", "\"locale.hi\"", "\"locale.hindi\"")
        assertViolation(fx.verifier().checkI18n(), "catalog.locales.hi", "\"locale.hi\"", "missing from i18n/hi.json")
    }

    // ---- tokens ---------------------------------------------------------------------------------

    @Test fun `low contrast swatch fails`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"hex\": \"#37474F\"", "\"hex\": \"#7FA7FF\"")
        assertViolation(fx.verifier().checkSwatchContrast(), "accentSwatches.slate", "(< 4.5)")
    }

    @Test fun `contrast math`() {
        assertEquals(21.0, Contrast.ratioAgainstWhite("#000000")!!, 0.01)
        assertEquals(1.0, Contrast.ratioAgainstWhite("#FFFFFF")!!, 0.01)
        assertEquals(8.07, Contrast.ratioAgainstWhite("#1F4AA8")!!, 0.05)
    }

    @Test fun `wrong text size fails`() {
        val fx = Fixture()
        fx.replace("shared/design-tokens.json", "\"size\": 23", "\"size\": 24")
        assertViolation(fx.verifier().checkTypeAndSpace(), "text.m is 24", "= 23")
    }

    @Test fun `more than four text sizes fail`() {
        val fx = Fixture()
        fx.replace("shared/design-tokens.json", "\"hiLineExtra\": 2", "\"hiLineExtra\": 2, \"xxl\": { \"size\": 109, \"line\": 116 }")
        assertViolation(fx.verifier().checkTypeAndSpace(), "5 text sizes", "at most 4")
    }

    @Test fun `space must have exactly three values`() {
        val fx = Fixture()
        fx.replace("shared/design-tokens.json", "\"space\": { \"s\": 8, \"m\": 16, \"l\": 24 }", "\"space\": { \"s\": 8, \"m\": 16, \"l\": 24, \"xl\": 32 }")
        assertViolation(fx.verifier().checkTypeAndSpace(), "\"space\" must have exactly 3 values (has 4)")
    }

    // ---- schema / templates ---------------------------------------------------------------------

    @Test fun `unknown field type and bad optionsFrom fail`() {
        val fx = Fixture()
        fx.replace("shared/site-schema.json", "\"type\": \"image\"", "\"type\": \"video\"")
        fx.replace("shared/site-schema.json", "\"catalog.interests\"", "\"catalog.nothing\"")
        val v = fx.verifier().checkSchemaAndTemplates()
        assertViolation(v, "site-schema.hero.photo", "unknown field type \"video\"")
        assertViolation(v, "site-schema.services.offered", "optionsFrom \"catalog.nothing\"")
    }

    @Test fun `template with unknown accent fails`() {
        val fx = Fixture()
        fx.replace("shared/templates/index.json", "\"defaultAccent\": \"indigo\"", "\"defaultAccent\": \"pink\"")
        assertViolation(fx.verifier().checkSchemaAndTemplates(), "classic", "defaultAccent \"pink\"")
    }

    // ---- hand-written strings -------------------------------------------------------------------

    @Test fun `string without Hindi translation fails`() {
        val fx = Fixture()
        fx.replace("composeResources/values/strings.xml", "</resources>", "    <string name=\"save\">Save</string>\n</resources>")
        assertViolation(fx.verifier().checkHandWrittenStrings(), "values/strings.xml", "\"save\" has no Hindi translation")
    }

    @Test fun `cat_ prefix is reserved for generated strings`() {
        val fx = Fixture()
        fx.replace("composeResources/values/strings.xml", "\"retry\"", "\"cat_retry\"")
        fx.replace("composeResources/values-hi/strings.xml", "\"retry\"", "\"cat_retry\"")
        assertViolation(fx.verifier().checkHandWrittenStrings(), "\"cat_retry\" uses the cat_ prefix")
    }

    // ---- Kotlin spacing -------------------------------------------------------------------------

    @Test fun `raw sp outside designsystem fails`() {
        val fx = Fixture()
        fx.writeKotlin("com/x/feature/home/Home.kt", "val style = TextStyle(fontSize = 18.sp)\n")
        assertViolation(fx.verifier().checkKotlinSpacing(), "feature/home/Home.kt:1:", "raw .sp literal \"18.sp\"")
    }

    @Test fun `raw dp spacing outside designsystem fails`() {
        val fx = Fixture()
        fx.writeKotlin("com/x/feature/home/Home.kt", """
            fun A() {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(Modifier.height(32.dp))
                }
            }
        """.trimIndent())
        val v = fx.verifier().checkKotlinSpacing()
        assertViolation(v, "Home.kt:2:", "\"12.dp\" in padding(...)")
        assertViolation(v, "\"4.dp\" in spacedBy(...)")
        assertViolation(v, "Home.kt:3:", "\"32.dp\" in height(...)")
        assertEquals(3, v.size, v.toString())
    }

    @Test fun `tokens, zero, sizes, comments, strings and designsystem are allowed`() {
        val fx = Fixture()
        fx.writeKotlin("com/x/feature/home/Home.kt", """
            // padding(8.dp) and 14.sp in a comment are fine
            /* Spacer(Modifier.height(8.dp)) */
            fun A() {
                val s = "padding(8.dp) 14.sp"
                Column(Modifier.padding(Tokens.Space.m).size(48.dp)) {
                    Spacer(Modifier.height(0.dp))
                    Icon(modifier = Modifier.size(24.dp))
                }
            }
        """.trimIndent())
        fx.writeKotlin("com/x/core/designsystem/Type.kt", "val body = TextStyle(fontSize = 14.sp); val p = Modifier.padding(12.dp)\n")
        assertEquals(emptyList(), fx.verifier().checkKotlinSpacing())
    }

    @Test fun `scanner reports line and column`() {
        assertEquals(listOf("2:18: raw .sp literal \"20.sp\"; use the design system type styles (Tokens.Text)"),
            KotlinSpacingScanner.scan("val a = 1\nval lineHeight = 20.sp\n"))
    }
}
