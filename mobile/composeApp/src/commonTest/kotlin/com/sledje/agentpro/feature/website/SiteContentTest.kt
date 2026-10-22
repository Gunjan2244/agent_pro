package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.feature.website.data.PublishError
import com.sledje.agentpro.feature.website.data.PublishIssues
import com.sledje.agentpro.feature.website.data.SiteContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The draft content document: order, visibility, schema values and the 64 KB cap (docs/WEB_RENDERER.md). */
class SiteContentTest {
    private fun section(key: String) = SiteSchema.section(key)!!
    private val empty = JsonObject(emptyMap())

    @Test
    fun companiesIsAFieldlessMovableSectionRightAfterAbout() {
        val companies = section("companies")
        assertTrue(companies.fields.isEmpty() && !companies.pinned)
        val keys = SiteContent.orderedSections(empty).map { it.key }
        assertEquals(keys.indexOf("about") + 1, keys.indexOf("companies"))
        // a saved order that predates the section: the row goes after the schema section before it, not last
        val old = buildJsonObject { put(SiteContent.SECTION_ORDER, buildJsonArray { listOf("hero", "services", "about", "faq", "contact").forEach { add(it) } }) }
        val oldKeys = SiteContent.orderedSections(old).map { it.key }
        assertEquals(oldKeys.indexOf("about") + 1, oldKeys.indexOf("companies"))
        // it can be hidden and moved; Done writes it into the order
        val hidden = SiteContent.withHidden(empty, companies, true)
        assertTrue(SiteContent.isHidden(hidden, companies))
        val moved = SiteContent.move(empty, 0, 3)
        assertTrue("companies" in moved[SiteContent.SECTION_ORDER]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(SiteContent.Summary.Empty, SiteContent.summary(empty, companies))
    }

    @Test
    fun defaultOrderIsTheSchemaOrderWithPinnedHeroFirstAndContactLast() {
        val keys = SiteContent.orderedSections(empty).map { it.key }
        assertEquals(SiteContent.sections.map { it.key }, keys)
        assertTrue("offers" !in keys, "the offers section stays hidden until its editor exists")
        assertEquals("hero", keys.first())
        assertEquals("contact", keys.last())
    }

    @Test
    fun storedOrderIsFollowedUnknownKeysDroppedMissingAppendedPinnedKeptInPlace() {
        val content = buildJsonObject {
            put(SiteContent.SECTION_ORDER, buildJsonArray { add("faq"); add("contact"); add("nope"); add("about"); add("hero"); add("faq") })
        }
        val keys = SiteContent.orderedSections(content).map { it.key }
        assertEquals(listOf("hero", "faq", "about", "companies", "services", "highlights", "achievements", "testimonials", "contact"), keys)
    }

    @Test
    fun hidingAndShowingASectionPinnedOnesStayVisible() {
        val hidden = SiteContent.withHidden(empty, section("faq"), hidden = true)
        assertTrue(SiteContent.isHidden(hidden, section("faq")))
        assertFalse(SiteContent.isHidden(SiteContent.withHidden(hidden, section("faq"), hidden = false), section("faq")))
        assertEquals(hidden, SiteContent.withHidden(hidden, section("hero"), hidden = true), "a pinned section can't be hidden")
        val forced = buildJsonObject { put(SiteContent.HIDDEN, buildJsonArray { add("contact") }) }
        assertFalse(SiteContent.isHidden(forced, section("contact")), "pinned sections ignore content.hidden")
    }

    @Test
    fun movingASectionWritesTheFullOrderWithPinnedSectionsFixed() {
        val movable = SiteContent.movableSections(empty).map { it.key }
        assertEquals(listOf("about", "companies", "services", "highlights", "achievements", "testimonials", "faq"), movable)
        val moved = SiteContent.move(empty, from = 6, to = 0)
        val order = moved[SiteContent.SECTION_ORDER]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("hero", "faq", "about", "companies", "services", "highlights", "achievements", "testimonials", "contact"), order)
        assertEquals(moved, SiteContent.move(moved, 9, 0), "out-of-range moves change nothing")
    }

    @Test
    fun sectionValuesRoundTripThroughTheForm() {
        val about = section("about")
        val content = buildJsonObject {
            put("about", buildJsonObject {
                put("bio", "Helping families since 2005")
                put("yearsExperience", 18)
                put("languagesSpoken", buildJsonArray { add("hindi"); add("marathi") })
                put("showAgencyCode", true)
                put("legacyKey", "kept")
            })
        }
        val values = SiteContent.sectionValues(content, about)
        assertEquals("18", values["yearsExperience"], "numbers become form text")
        assertEquals(listOf("hindi", "marathi"), values["languagesSpoken"])
        assertEquals(true, values["showAgencyCode"])

        val form = FormState(about.fields, values)
        form.set("bio", "  ")
        form.set("clientsServed", "250")
        val saved = SiteContent.withSection(content, about, form.values())["about"]!!.jsonObject
        assertNull(saved["bio"], "blank values are left out")
        assertEquals(JsonPrimitive(250L), saved["clientsServed"])
        assertEquals(JsonPrimitive(18L), saved["yearsExperience"])
        assertEquals("kept", saved["legacyKey"]!!.jsonPrimitive.content, "unknown keys are kept")
    }

    @Test
    fun listFieldsRoundTrip() {
        val faq = section("faq")
        val form = FormState(faq.fields)
        form.addItem("items")
        form.items("items")[0].set("question", "What is term insurance?")
        form.items("items")[0].set("answer", "Pure protection.")
        val content = SiteContent.withSection(empty, faq, form.values())
        val items = content["faq"]!!.jsonObject["items"] as JsonArray
        assertEquals("What is term insurance?", items[0].jsonObject["question"]!!.jsonPrimitive.content)
        val back = SiteContent.sectionValues(content, faq)["items"] as List<*>
        assertEquals(1, back.size)
    }

    @Test
    fun contentOver64KbIsRefused() {
        val big = buildJsonObject { put("about", buildJsonObject { put("bio", "x".repeat(SiteContent.MAX_BYTES)) }) }
        assertFalse(SiteContent.fitsSizeCap(big))
        assertTrue(SiteContent.fitsSizeCap(empty))
    }

    @Test
    fun summariesShowFirstTextOrItemCountOrEmpty() {
        val content = buildJsonObject {
            put("hero", buildJsonObject { put("headline", "Ramesh Kumar\nSecond line") })
            put("faq", buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("question", "Q") }) }) })
        }
        assertEquals(SiteContent.Summary.Text("Ramesh Kumar"), SiteContent.summary(content, section("hero")))
        assertEquals(SiteContent.Summary.Items(1), SiteContent.summary(content, section("faq")))
        assertEquals(SiteContent.Summary.Empty, SiteContent.summary(content, section("testimonials")))
    }

    @Test
    fun publishErrorsMapToSchemaFieldsAndValidationErrors() {
        val issues = PublishIssues.resolve(
            listOf(
                PublishError("hero", "headline", null, "required"),
                PublishError("about", "bio", null, "maxLength"),
                PublishError("faq", "answer", 1, "required"),
                PublishError("faq", "items", null, "maxItems"),
                PublishError("contact", "email", null, "format"),
                PublishError("about", "yearsExperience", null, "range"),
                PublishError("unknown", "x", null, "required"),
            ),
        )
        assertEquals(6, issues.size, "unknown sections are dropped")
        assertEquals(ValidationError.Required, issues[0].error)
        assertEquals(ValidationError.MaxLength(600), issues[1].error)
        assertEquals("answer", issues[2].field?.key)
        assertEquals("items", issues[2].listField?.key)
        assertEquals(1, issues[2].item)
        assertEquals(ValidationError.MaxItems(8), issues[3].error)
        assertEquals(ValidationError.Email, issues[4].error)
        assertEquals(ValidationError.Range(0, 70), issues[5].error)
        assertEquals(FieldType.Email, issues[4].field?.type)
    }

    @Test
    fun movingSectionsKeepsTheStoredOffersPlace() {
        val content = buildJsonObject {
            put("sectionOrder", JsonArray(listOf("hero", "offers", "about", "services", "faq", "contact").map(::JsonPrimitive)))
        }
        val moved = SiteContent.move(content, 0, 1)
        val order = moved["sectionOrder"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals("hero", order.first())
        assertTrue("offers" in order, "offers isn't stripped by a move")
        assertEquals(order.indexOf("hero") + 1, order.indexOf("offers"), "still right after hero")
    }

    @Test
    fun offersAfterHeroUnhidesAndMovesOnlyOffers() {
        val content = buildJsonObject {
            put("hidden", JsonArray(listOf("offers", "faq").map(::JsonPrimitive)))
        }
        val out = SiteContent.withOffersAfterHero(content, listOf("hero", "about", "offers", "contact"))
        assertEquals(listOf("faq"), out["hidden"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("hero", "offers", "about", "contact"), out["sectionOrder"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
