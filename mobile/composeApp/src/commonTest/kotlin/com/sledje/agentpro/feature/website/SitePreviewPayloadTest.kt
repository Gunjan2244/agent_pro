package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.platform.SitePreviewScript
import com.sledje.agentpro.feature.website.data.SitePreviewPayload
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The JSON the in-app preview hands to `window.renderSite` (docs/WEB_RENDERER.md). The WebView itself
 * can't render under Robolectric, so this is what's tested instead.
 */
class SitePreviewPayloadTest {
    private val draft = buildJsonObject {
        put("hero", buildJsonObject { put("headline", "Unsaved headline") })
        put("about", buildJsonObject { put("showAgencyCode", true) })
    }
    private val site = testSite().copy(draftContent = draft, accent = AccentSwatch.Teal, locale = "hi")
    private val profile = testProfile().copy(agencyCode = "AG123", branch = "Pune 2", photoUrl = "https://example.com/p.webp")

    @Test
    fun payloadHasTheRenderSiteShapeWithTheDraftAsContent() {
        val json = SitePreviewPayload.build(site, profile)
        assertEquals(setOf("content", "portal", "template_key", "accent", "locale", "profile"), json.keys)
        assertEquals(draft, json["content"])
        assertEquals("classic", json["template_key"]!!.jsonPrimitive.content)
        assertEquals("teal", json["accent"]!!.jsonPrimitive.content, "the swatch key, never the hex")
        assertEquals("hi", json["locale"]!!.jsonPrimitive.content)
    }

    @Test
    fun portalIsTrueOnlyWhenTheFlagIsOnAndTheAgentHasNotSwitchedTheLoginOff() {
        fun portal(content: JsonObject, enabled: Boolean) =
            SitePreviewPayload.build(site, profile, content, portalEnabled = enabled)["portal"]!!.jsonPrimitive.content
        fun contact(show: Boolean) = buildJsonObject { put("contact", buildJsonObject { put("showCustomerLogin", show) }) }
        assertEquals("false", portal(draft, enabled = false), "flag off")
        assertEquals("true", portal(draft, enabled = true), "flag on, switch never touched = on")
        assertEquals("true", portal(contact(true), enabled = true))
        assertEquals("false", portal(contact(false), enabled = true), "the agent's own switch is off")
        assertEquals("false", portal(contact(true), enabled = false))
        assertEquals("false", SitePreviewPayload.build(site, profile)["portal"]!!.jsonPrimitive.content, "default = off")
        assertTrue(SitePreviewPayload.json(site, profile, portalEnabled = true).contains("\"portal\":true"))
    }

    @Test
    fun previewOffersAnswerPassesCompaniesCompanyAndOffersThrough() {
        val answer = RowJson.parseToJsonElement(
            """{"companies":[{"name":"Alpha Insurance","insurance_types":["life","health"]}],"company":{"disclaimer":"T&C"},"offers":{"featured":null,"items":[]}}""",
        ).jsonObject
        val json = SitePreviewPayload.buildWithPreviewOffers(site, profile, draft, "classic", answer)
        assertEquals(answer["companies"], json["companies"])
        assertEquals(answer["company"], json["company"])
        assertEquals(answer["offers"], json["offers"])
        val first = json["companies"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("name", "insurance_types"), first.keys, "names and types only, as sent")
    }

    @Test
    fun anOlderServerAnswerWithoutCompaniesLeavesTheKeyOut() {
        val answer = RowJson.parseToJsonElement("""{"company":null,"offers":{"featured":null,"items":[]}}""").jsonObject
        assertFalse("companies" in SitePreviewPayload.buildWithPreviewOffers(site, profile, draft, "classic", answer))
        assertFalse("companies" in SitePreviewPayload.build(site, profile))
    }

    @Test
    fun profileHasOnlyPublicFieldsAndTheAgencyCodeOnlyWhenShown() {
        val shown = SitePreviewPayload.build(site, profile)["profile"]!!.jsonObject
        assertEquals(setOf("full_name", "designation", "city", "photo_url", "phone", "agency_code"), shown.keys)
        assertFalse("email" in shown || "branch" in shown || "id" in shown, "never private fields")
        val hidden = SitePreviewPayload.build(site.copy(draftContent = JsonObject(emptyMap())), profile)["profile"]!!.jsonObject
        assertFalse("agency_code" in hidden)
    }

    @Test
    fun templatePickerPreviewsOtherTemplatesWithTheSameContent() {
        val json = SitePreviewPayload.build(site.copy(accent = null), profile, templateKey = "modern")
        assertEquals("modern", json["template_key"]!!.jsonPrimitive.content)
        assertEquals("teal", json["accent"]!!.jsonPrimitive.content, "no accent → the template's default")
        assertEquals(draft, json["content"])
    }

    @Test
    fun renderCallPassesTheJsonAsAnEscapedStringLiteral() {
        val tricky = SitePreviewPayload.json(site.copy(draftContent = buildJsonObject { put("hero", buildJsonObject { put("headline", "\"Quote\" </script>   \\ नमस्ते") }) }), profile)
        val call = SitePreviewScript.renderCall(tricky)
        assertTrue(call.startsWith("(function r(){if(window.renderSite){window.renderSite(\""), call)
        assertFalse(' ' in call, "line separators are escaped")
        val literal = call.substringAfter("window.renderSite(").substringBefore(");}else")
        val decoded = RowJson.decodeFromString(JsonPrimitive.serializer(), literal.replace("\\u2028", " ")).content
        assertEquals(tricky, decoded, "the page gets exactly the payload JSON")
        assertEquals("/index.html?preview=1", SitePreviewScript.PAGE_PATH)
    }
}
