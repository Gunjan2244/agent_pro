package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.catalog.SiteSeed
import com.sledje.agentpro.core.catalog.TemplateFamily
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.Validators
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.StarterContent
import com.sledje.agentpro.testing.testProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterContentTest {
    private val classic = Templates.fromKey("classic")!!
    private val spotlight = Templates.fromKey("spotlight")!!
    private val profile = testProfile()

    private fun starter(
        template: com.sledje.agentpro.core.catalog.TemplateSpec = classic,
        locale: String = "en",
        agentId: String = "agent-1",
        p: com.sledje.agentpro.feature.settings.data.Profile = profile,
    ) = StarterContent.from(p, template, locale, agentId)

    private fun JsonObject.section(key: String) = this[key]!!.jsonObject
    private fun JsonObject.text(section: String, field: String) = section(section)[field]!!.jsonPrimitive.content
    private fun JsonObject.items(section: String) = section(section)["items"]!!.jsonArray.map { it.jsonObject }

    /** A local copy of the publish rules (docs/BACKEND.md, Publish validation) for the sections that are shown. */
    private fun publishErrors(content: JsonObject): List<String> {
        val hidden = (content[SiteContent.HIDDEN] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()
        val errors = mutableListOf<String>()
        fun check(where: String, spec: FieldSpec, value: JsonElement?) {
            if (value == null) {
                if (spec.required) errors += "$where.${spec.key}: required"
                return
            }
            when (spec.type) {
                FieldType.Text, FieldType.Multiline -> {
                    val text = value.jsonPrimitive.content
                    if (text.isBlank()) errors += "$where.${spec.key}: blank"
                    if (spec.maxLength != null && text.length > spec.maxLength) errors += "$where.${spec.key}: maxLength ${text.length}"
                }
                FieldType.Phone -> if (Validators.phone(value.jsonPrimitive.content) != null) errors += "$where.${spec.key}: phone"
                FieldType.Email -> if (Validators.email(value.jsonPrimitive.content) != null) errors += "$where.${spec.key}: email"
                FieldType.Select -> if (spec.options.isNotEmpty() && spec.options.none { it.key == value.jsonPrimitive.content }) errors += "$where.${spec.key}: option"
                FieldType.MultiSelect -> value.jsonArray.forEach { k ->
                    if (spec.options.none { it.key == k.jsonPrimitive.content }) errors += "$where.${spec.key}: option ${k.jsonPrimitive.content}"
                }
                FieldType.ItemList -> {
                    val list = value.jsonArray
                    if (spec.maxItems != null && list.size > spec.maxItems) errors += "$where.${spec.key}: maxItems"
                    list.forEachIndexed { i, item -> spec.itemFields.forEach { f -> check("$where.${spec.key}[$i]", f, item.jsonObject[f.key]) } }
                }
                else -> Unit
            }
        }
        SiteSchema.sections.filter { it.pinned || it.key !in hidden }.forEach { section ->
            val obj = content[section.key] as? JsonObject
            section.fields.forEach { check(section.key, it, obj?.get(it.key)) }
        }
        return errors
    }

    @Test
    fun everyGeneralSectionIsFilledInEnglishAndHindi() {
        for (locale in listOf("en", "hi")) {
            val c = starter(locale = locale)
            assertTrue(c.text("hero", "headline").isNotBlank(), locale)
            assertTrue(c.text("hero", "subheadline").isNotBlank(), locale)
            assertTrue(c.text("about", "bio").isNotBlank(), locale)
            assertEquals("LIC Agent", c.text("about", "designation"))
            assertEquals(listOf(if (locale == "hi") "hindi" else "english"), c.section("about")["languagesSpoken"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(5, c.section("services")["offered"]!!.jsonArray.size, locale)
            assertEquals(5, c.items("services").size, locale)
            assertTrue(c.items("services").all { it["description"]!!.jsonPrimitive.content.isNotBlank() }, locale)
            assertEquals(4, c.items("highlights").size, locale)
            assertEquals(5, c.items("faq").size, locale)
            assertEquals("+919876543210", c.text("contact", "phone"))
            assertEquals(c.text("contact", "phone"), c.text("contact", "whatsapp"), "WhatsApp defaults to the phone")
            assertEquals("ramesh@example.com", c.text("contact", "email"))
        }
        assertTrue(starter(locale = "hi").text("hero", "headline").contains("Ramesh Kumar"))
        assertNotEquals(starter(locale = "en").text("hero", "headline"), starter(locale = "hi").text("hero", "headline"))
        assertEquals(starter(locale = "en"), starter(locale = "xx"), "an unknown locale falls back to the default")
    }

    @Test
    fun variantChoiceIsDeterministicPerAgentAndVariesAcrossAgents() {
        assertEquals(starter(agentId = "agent-1"), starter(agentId = "agent-1"))
        val bios = (1..40).map { starter(agentId = "agent-$it").text("about", "bio") }.toSet()
        assertTrue(bios.size > 1, "different agents get different bios")
        val expected = SiteSeed.pick("agent-1", SiteSeed.salt("hero", "headline"), 3)
        val headlines = listOf("Namaste, I'm Ramesh Kumar", "Hello, I'm Ramesh Kumar", "Hi, I'm Ramesh Kumar")
        assertEquals(headlines[expected], starter(agentId = "agent-1").text("hero", "headline"))
    }

    @Test
    fun blankCityOrDesignationUsesTheFallbackText() {
        val noCity = starter(p = testProfile(city = null))
        val noDesignation = starter(p = testProfile(designation = null))
        for (agent in (1..30).map { "agent-$it" }) {
            val c = starter(p = testProfile(city = " ", designation = null), agentId = agent)
            listOf(c.text("hero", "subheadline"), c.text("about", "bio")).forEach { t ->
                assertFalse(t.contains("{"), t)
                assertFalse(t.contains("  "), "no gap where the value was: $t")
            }
        }
        assertNull(noDesignation.section("about")["designation"], "no designation in the profile → none seeded")
        assertFalse(noCity.text("about", "bio").contains("Pune"))
    }

    @Test
    fun everyTextIsWithinItsMaxLengthEvenForALongName() {
        val long = testProfile(fullName = "R".repeat(200), designation = "D".repeat(200), city = "C".repeat(200))
        for (locale in listOf("en", "hi")) for (t in Templates.all) {
            val c = starter(t, locale, p = long)
            assertEquals(emptyList(), publishErrors(c).filter { it.contains("maxLength") }, "$locale ${t.key}")
        }
        assertEquals(70, starter(p = long).text("hero", "headline").length, "cut to the headline limit")
    }

    @Test
    fun hiddenSectionsAndOrderFollowTheTemplateFamily() {
        fun hidden(c: JsonObject) = c[SiteContent.HIDDEN]!!.jsonArray.map { it.jsonPrimitive.content }
        fun order(c: JsonObject) = c[SiteContent.SECTION_ORDER]!!.jsonArray.map { it.jsonPrimitive.content }
        val personal = starter(classic)
        val offers = starter(spotlight)
        assertEquals(classic.sections, order(personal))
        assertEquals(spotlight.sections, order(offers))
        assertEquals(setOf("achievements", "testimonials", "offers"), hidden(personal).toSet())
        assertEquals(setOf("achievements", "testimonials"), hidden(offers).toSet())
        assertEquals(SiteSeed.hiddenSections(TemplateFamily.Offers.key), hidden(offers))
        assertEquals("offers", order(offers)[1], "offers follows hero in the offers family")
        assertNotEquals(order(personal), order(offers))
    }

    @Test
    fun theDraftPassesThePublishRulesForEveryTemplateAndLocale() {
        for (locale in listOf("en", "hi")) for (t in Templates.all) {
            assertEquals(emptyList(), publishErrors(starter(t, locale)), "$locale ${t.key}")
        }
        assertTrue(SiteContent.fitsSizeCap(starter()))
        // The minimum profile (name and mobile only) is still publishable.
        val minimal = testProfile(designation = null, city = null).copy(email = null)
        assertEquals(emptyList(), publishErrors(starter(p = minimal)))
        assertNull(starter(p = minimal).section("contact")["email"])
    }

    @Test
    fun socialProofIsNeverInvented() {
        for (t in Templates.all) {
            val c = starter(t)
            assertNull(c["testimonials"], t.key)
            assertNull(c["achievements"], t.key)
            val about = c.section("about")
            assertNull(about["yearsExperience"])
            assertNull(about["clientsServed"])
            assertNull(c["offers"], "offers come from the server, not the seed")
        }
    }

    @Test
    fun theSeededBioIsNotTheAgentsOwnUntilTheyChangeIt() {
        for (locale in listOf("en", "hi")) for (agent in (1..12).map { "a$it" }) {
            val bio = starter(locale = locale, agentId = agent).text("about", "bio")
            assertFalse(StarterContent.isOwnBio(bio), "$locale $agent")
            assertFalse(StarterContent.isOwnBio("  $bio\n"), "whitespace doesn't count as a change")
            assertTrue(StarterContent.isOwnBio("$bio I have 10 years of experience."))
        }
        // Still recognised after the profile's city or designation changed.
        val noCity = starter(p = testProfile(city = null)).text("about", "bio")
        assertFalse(StarterContent.isOwnBio(noCity))
        assertFalse(StarterContent.isOwnBio(null))
        assertFalse(StarterContent.isOwnBio("   "))
        assertTrue(StarterContent.isOwnBio("I have helped families in Pune since 2009."))
    }

    @Test
    fun keysMatchTheSchema() {
        assertTrue(SiteSchema.section(StarterContent.HERO)!!.fields.single { it.key == StarterContent.HEADLINE }.required)
        assertTrue(SiteSchema.section(StarterContent.CONTACT)!!.fields.any { it.key == StarterContent.PHONE && it.required })
        assertTrue(SiteSchema.section(StarterContent.ABOUT)!!.fields.any { it.key == StarterContent.DESIGNATION })
        assertTrue(SiteSchema.section(StarterContent.ABOUT)!!.fields.any { it.key == StarterContent.BIO })
        assertEquals(JsonPrimitive("Ramesh"), JsonPrimitive(StarterContent.placeholderValues(profile)["firstName"]))
    }
}
