package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.AppConfigKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.CatalogKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.Naming
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitionsException
import com.sledje.agentpro.buildlogic.codegen.SiteSchemaKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.StringsXmlGenerator
import com.sledje.agentpro.buildlogic.codegen.TemplatesKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.TokensKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.long
import com.sledje.agentpro.buildlogic.codegen.string
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratorsTest {

    /** Every catalog entry appears as an enum constant with its key, label, icon, tone and order. */
    private fun assertEnumsMatchJson(defs: SharedDefinitions) {
        val code = CatalogKotlinGenerator(defs).generate()
        for (list in defs.catalogLists.filterNot { it.jsonName == "privacyNotices" }) { // own file, PrivacyNotices.kt
            val body = code.substringAfter("enum class ${list.kotlinName}(").substringBefore("companion object")
            assertTrue(body.isNotEmpty(), "enum ${list.kotlinName} generated")
            val constants = Regex("""(?m)^    (\w+)\((".*)\),$""").findAll(body).toList()
            assertEquals(list.keys, constants.map { Regex("^\"([^\"]+)\"").find(it.groupValues[2])!!.groupValues[1] },
                "keys of ${list.kotlinName} in order")
            list.entries.forEachIndexed { i, e ->
                val (name, args) = constants[i].destructured
                assertEquals(Naming.enumConstant(e.string("key")!!), name)
                assertTrue("Res.string.${Naming.stringResourceName(e.string("labelKey")!!)}" in args, args)
                e.string("icon")?.let { assertTrue("Res.drawable.ic_$it" in args, args) }
                e.string("tone")?.let { assertTrue("Tone.${Naming.enumConstant(it)}" in args, args) }
                assertTrue(", ${e.long("order")}" in args, args)
            }
        }
    }

    @Test fun `generated enums match the fixture JSON`() {
        val defs = Fixture().defs()
        assertEnumsMatchJson(defs)
        val code = CatalogKotlinGenerator(defs).generate()
        assertTrue("enum class EnquiryStatus(" in code)
        assertTrue("FollowUp(\"follow_up\", Res.string.cat_enquiry_status_follow_up, Res.drawable.ic_event, Tone.Warning, 2, terminal = false)," in code)
        assertTrue("Overdue(\"overdue\", Res.string.cat_due_bucket_overdue, Res.drawable.ic_error, Tone.Danger, 1, minDays = null, maxDays = -1)," in code)
        assertTrue("val minDays: Int?," in code)
        assertTrue("HalfYearly(\"half_yearly\", Res.string.cat_premium_mode_half_yearly, Res.drawable.ic_date_range, Tone.Neutral, 2, months = 6)," in code)
        assertTrue("enum class AccentSwatch(" in code && "Indigo(\"indigo\", Res.string.cat_swatch_indigo, 1, hex = \"#1F4AA8\")," in code)
        assertTrue(") : CatalogEntry {" in code.substringAfter("enum class AccentSwatch("))
        assertTrue("enum class FeatureId(" in code && "SiteBuilder(\"siteBuilder\", Res.string.cat_feature_siteBuilder, Res.drawable.ic_web, 1, default = true)," in code)
        assertTrue("enum class Tone(val key: String) {" in code && "Neutral(\"neutral\")," in code)
        assertTrue("\"Agent's \\\"Best\\\" Plan\"," in code, "plan suggestions are escaped")
        assertTrue("object Pricing {" in code && "const val purchaseInr: Int = 499" in code && "val reminderDays: List<Int> = listOf(30, 7, 1)" in code)
        assertTrue("val allowedTlds: List<String> = listOf(\".in\", \".com\")" in code)
        assertTrue("const val VERSION: Int = 1" in code)
    }

    @Test fun `generated enums match the real shared JSON`() {
        val dir = realSharedDir() ?: return
        val defs = try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return }
        assertEnumsMatchJson(defs)
        val code = CatalogKotlinGenerator(defs).generate()
        listOf("EnquiryStatus", "Interest", "EnquirySource", "PremiumMode", "PolicyStatus", "PlanStatus",
            "DomainStatus", "DueBucket", "FeatureId", "AccentSwatch", "AppLocale").forEach { assertTrue("enum class $it(" in code, it) }
        assertTrue("object PlanSuggestions {" in code && "object Pricing {" in code && "object ReservedSlugs {" in code)
    }

    @Test fun `reserved slugs and locales are generated`() {
        val code = CatalogKotlinGenerator(Fixture().defs()).generate()
        assertTrue("object ReservedSlugs {\n    val all: List<String> = listOf(\n        \"admin\",\n        \"agent-pro\",\n    )\n}" in code, code)
        assertTrue("enum class AppLocale(" in code)
        assertTrue("En(\"en\", Res.string.cat_locale_en, 1)," in code && "Hi(\"hi\", Res.string.cat_locale_hi, 2)," in code)
        assertTrue(") : CatalogEntry {" in code.substringAfter("enum class AppLocale("))
        assertEquals("AppLocale", Naming.catalogTypeName("locales"))
    }

    @Test fun `deprecated entries are kept and flagged`() {
        val fx = Fixture()
        fx.replace("shared/catalog.json", "\"order\": 2, \"terminal\": false }", "\"order\": 2, \"terminal\": false, \"deprecated\": true }")
        val code = CatalogKotlinGenerator(fx.defs()).generate()
        assertTrue("FollowUp(\"follow_up\", Res.string.cat_enquiry_status_follow_up, Res.drawable.ic_event, Tone.Warning, 2, terminal = false, deprecated = true)," in code)
    }

    @Test fun `naming conventions`() {
        assertEquals("cat_enquiry_status_follow_up", Naming.stringResourceName("enquiry_status.follow_up"))
        assertEquals("cat_template_classic_name", Naming.stringResourceName("template.classic.name"))
        assertEquals("EnquiryStatus", Naming.catalogTypeName("enquiryStatuses"))
        assertEquals("EnquirySource", Naming.catalogTypeName("enquirySources"))
        assertEquals("AccentSwatch", Naming.catalogTypeName("accentSwatches"))
        assertEquals("DueBucket", Naming.catalogTypeName("dueBuckets"))
        assertEquals("FeatureId", Naming.catalogTypeName("features"))
        assertEquals("Next3Months", Naming.enumConstant("next_3_months"))
        assertEquals("SiteBuilder", Naming.enumConstant("siteBuilder"))
        assertEquals("\"a\\\$b\\\"c\\\\\"", Naming.kotlinString("a\$b\"c\\"))
    }

    @Test fun `strings xml uses Compose resources escaping`() {
        val xml = StringsXmlGenerator.generate(
            mapOf("b.key" to "It's \"fine\" & <ok>", "a.key" to "line1\nline2 \\ end"), "en"
        )
        assertTrue("<string name=\"cat_b_key\">It's \"fine\" &amp; &lt;ok&gt;</string>" in xml, xml)
        assertTrue("<string name=\"cat_a_key\">line1\\nline2 \\\\ end</string>" in xml, xml)
        assertTrue(xml.indexOf("cat_a_key") < xml.indexOf("cat_b_key"), "sorted")
        // Well-formed XML whose text round-trips to what Compose then unescapes.
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals("It's \"fine\" & <ok>", doc.getElementsByTagName("string").item(1).textContent)
    }

    @Test fun `strings xml covers every i18n key in both locales`() {
        val defs = Fixture().defs()
        val en = StringsXmlGenerator.generate(defs.i18nEn, "en")
        val hi = StringsXmlGenerator.generate(defs.i18nHi, "hi")
        defs.i18nEn.keys.forEach {
            assertTrue("name=\"${Naming.stringResourceName(it)}\"" in en, it)
            assertTrue("name=\"${Naming.stringResourceName(it)}\"" in hi, it)
        }
        assertTrue("धन्यवाद — {name} जल्द संपर्क करेंगे" in hi)
        assertTrue("This page isn't available" in en, "apostrophes stay literal")
    }

    @Test fun `site schema becomes SectionSpec and FieldSpec`() {
        val code = SiteSchemaKotlinGenerator(Fixture().defs()).generate()
        assertTrue("object SiteSchema {" in code)
        assertTrue("key = \"hero\"," in code && "icon = Res.drawable.ic_badge," in code && "pinned = true," in code)
        assertTrue("type = FieldType.Text," in code && "required = true," in code && "maxLength = 70," in code)
        assertTrue("aspect = \"1:1\"," in code)
        assertTrue("defaultText = Res.string.cat_site_cta_default," in code)
        assertTrue("min = 0L," in code && "max = 70L," in code)
        assertTrue("default = FieldDefault.Bool(false)," in code)
        assertTrue("options = Interest.active.map { FieldOption(it.key, it.label, it.icon) }," in code)
        assertTrue("optionsFrom = \"catalog.interests\"," in code)
        assertTrue("type = FieldType.ItemList," in code && "maxItems = 4," in code && "itemFields = listOf(" in code)
        assertTrue("FieldOption(\"shield\", Res.string.cat_highlight_icon_shield, icon = Res.drawable.ic_shield)," in code)
    }

    @Test fun `templates become TemplateSpec entries in display order`() {
        val code = TemplatesKotlinGenerator(Fixture().defs()).generate()
        assertTrue("data class TemplateSpec(" in code && "object Templates {" in code, code)
        assertTrue("const val VERSION: Int = 1" in code)
        assertTrue("key = \"classic\"," in code)
        assertTrue("name = Res.string.cat_template_classic_name," in code)
        assertTrue("description = Res.string.cat_template_classic_description," in code)
        assertTrue("sections = listOf(\"hero\", \"about\", \"services\")," in code)
        assertTrue("defaultAccent = AccentSwatch.Indigo," in code)
        assertTrue("preview = \"web/templates/classic/preview.webp\"," in code)
        assertTrue("fun fromKey(key: String): TemplateSpec? = all.firstOrNull { it.key == key }" in code)
    }

    @Test fun `templates are sorted by order and an unknown accent fails`() {
        val fx = Fixture()
        fx.file("shared/templates/index.json").writeText(
            """{ "version": 1, "templates": [
              { "key": "b", "nameKey": "template.classic.name", "descriptionKey": "template.classic.description",
                "sections": ["hero"], "defaultAccent": "indigo", "preview": "p", "order": 2 },
              { "key": "a", "nameKey": "template.classic.name", "descriptionKey": "template.classic.description",
                "sections": ["hero"], "defaultAccent": "indigo", "preview": "p", "order": 1 } ] }"""
        )
        val code = TemplatesKotlinGenerator(fx.defs()).generate()
        assertTrue(code.indexOf("key = \"a\"") < code.indexOf("key = \"b\""), "sorted by order")
        fx.replace("shared/templates/index.json", "\"defaultAccent\": \"indigo\", \"preview\": \"p\", \"order\": 1", "\"defaultAccent\": \"nope\", \"preview\": \"p\", \"order\": 1")
        assertFailsWith<IllegalArgumentException> { TemplatesKotlinGenerator(fx.defs()).generate() }
    }

    @Test fun `templates are generated from the real shared JSON`() {
        val dir = realSharedDir() ?: return
        val defs = try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return }
        val code = TemplatesKotlinGenerator(defs).generate()
        defs.templateKeys.forEach { assertTrue("key = \"$it\"," in code, it) }
    }

    @Test fun `every documented field type is mapped`() {
        val documented = listOf("text", "multiline", "number", "phone", "email", "url", "image", "select",
            "multiSelect", "toggle", "swatch", "date", "list")
        assertEquals(documented.toSet(), SiteSchemaKotlinGenerator.FIELD_TYPES.keys)
    }

    @Test fun `tokens are typed and the web-only size is left out`() {
        val code = TokensKotlinGenerator(Fixture().defs()).generate()
        assertTrue("object ColorLight {" in code && "val primary: Color = Color(0xFF1F4AA8)" in code)
        assertTrue("val info: ToneSet = ToneSet(" in code && "Tone.Warning -> warning" in code)
        assertTrue("val seriesLight: List<Color> = listOf(Color(0xFF2A78D6), Color(0xFFEB6834))" in code)
        assertTrue("val s: TextToken = TextToken(14.sp, 20.sp)" in code && "val l: TextToken = TextToken(39.sp, 46.sp)" in code)
        assertFalse("TextToken(65.sp" in code, "text.xl is web-only")
        assertTrue("val hiLineExtra: TextUnit = 2.sp" in code)
        assertTrue("object Space {" in code && "val m: Dp = 16.dp" in code)
        assertTrue("const val medium: Int = 250" in code)
    }

    @Test fun `app config uses empty strings for missing keys and escapes values`() {
        val code = AppConfigKotlinGenerator(mapOf("SUPABASE_URL" to "https://x.supabase.co", "SITE_BASE_URL" to "a\"b\$c")).generate()
        assertTrue("const val SUPABASE_URL: String = \"https://x.supabase.co\"" in code)
        assertTrue("const val SUPABASE_ANON_KEY: String = \"\"" in code)
        assertTrue("const val SITE_BASE_URL: String = \"a\\\"b\\\$c\"" in code)
    }

    @Test fun `loading reports every missing or invalid file`() {
        val fx = Fixture()
        File(fx.shared, "i18n/hi.json").delete()
        fx.file("shared/design-tokens.json").writeText("{ not json")
        val e = assertFailsWith<SharedDefinitionsException> { fx.defs() }
        assertTrue(e.problems.any { "i18n/hi.json is missing" in it }, e.problems.toString())
        assertTrue(e.problems.any { "design-tokens.json is not valid JSON" in it }, e.problems.toString())
    }

    @Test fun `keys lock only ever adds keys`() {
        val fx = Fixture()
        val existing = mapOf("interests" to listOf("old_removed", "term"))
        val merged = CatalogKeysLock.merged(existing, fx.defs())
        assertEquals(listOf("old_removed", "term", "money_back"), merged["interests"])
        val file = fx.file("lock.json")
        file.writeText(CatalogKeysLock.render(merged))
        assertEquals(merged, CatalogKeysLock.read(file))
    }
}
