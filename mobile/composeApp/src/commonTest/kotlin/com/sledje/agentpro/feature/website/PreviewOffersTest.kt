package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.website.data.PreviewOffersCache
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.SitePreviewPayload
import com.sledje.agentpro.feature.website.data.previewOffersOrNull
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real offers in the in-app previews (DECISIONS #84) and the Appearance preview after a switch (#86). */
class PreviewOffersTest {
    private val site = testSite()
    private val profile = testProfile()
    private val answer = buildJsonObject {
        put("company", buildJsonObject { put("disclaimer", "Insurance is a subject matter of solicitation.") })
        put("offers", buildJsonObject { put("featured", buildJsonObject { put("title", "Plan A") }); put("items", JsonArray(emptyList())) })
    }

    @Test
    fun payloadCarriesOffersAndCompanyWhenGiven() {
        val json = SitePreviewPayload.buildWithPreviewOffers(site, profile, site.draftContent, "spotlight", answer)
        assertEquals(answer["offers"], json["offers"])
        assertEquals(answer["company"], json["company"])
    }

    @Test
    fun payloadOmitsThemOtherwise() {
        assertFalse("offers" in SitePreviewPayload.build(site, profile))
        assertFalse("company" in SitePreviewPayload.build(site, profile))
        val nullCompany = buildJsonObject { put("company", kotlinx.serialization.json.JsonNull); put("offers", answer["offers"]!!) }
        val json = SitePreviewPayload.buildWithPreviewOffers(site, profile, site.draftContent, "spotlight", nullCompany)
        assertTrue("offers" in json)
        assertFalse("company" in json, "a null company is left out")
        assertFalse("offers" in SitePreviewPayload.buildWithPreviewOffers(site, profile, site.draftContent, "classic", null))
        assertTrue("offers" in Json(SitePreviewPayload.json(site, profile, previewOffers = answer)))
    }

    private fun Json(s: String): JsonObject = com.sledje.agentpro.core.data.RowJson.parseToJsonElement(s).jsonObject

    @Test
    fun cacheKeepsAnswersPerContentAndTemplate() {
        val cache = PreviewOffersCache(capacity = 2)
        val c = site.draftContent
        assertNull(cache.get(c, "spotlight"))
        cache.put(c, "spotlight", answer)
        assertSame(answer, cache.get(c, "spotlight"))
        assertNull(cache.get(c, "guide"), "another template is another entry")
        assertNull(cache.get(buildJsonObject { put("x", JsonPrimitive(1)) }, "spotlight"), "another draft is another entry")
        cache.put(c, "guide", answer)
        cache.put(c, "classic", answer)
        assertNull(cache.get(c, "spotlight"), "the oldest goes first past the capacity")
        assertSame(answer, cache.get(c, "classic"))
    }

    @Test
    fun aFailureMeansNoOffers() = runTest {
        val sites = FakeSiteRepository(site)
        assertNull(sites.previewOffersOrNull(site.draftContent, "spotlight"))
        sites.previewOffersResult = Result.Ok(answer)
        assertEquals(answer, sites.previewOffersOrNull(site.draftContent, "spotlight"))
        assertEquals(2, sites.previewOfferCalls.size)
        assertEquals("spotlight", sites.previewOfferCalls.first().second)
    }

    @Test
    fun crossFamilyPreviewShowsOffersAfterHeroAndSameFamilyChangesNothing() {
        val classic = Templates.fromKey("classic")!!
        val modern = Templates.fromKey("modern")!!
        val spotlight = Templates.fromKey("spotlight")!!
        val guide = Templates.fromKey("guide")!!
        val draft = buildJsonObject { put("hidden", JsonArray(listOf(JsonPrimitive("offers")))) }

        val toOffers = SiteContent.afterSwitch(draft, classic, spotlight)
        val order = toOffers["sectionOrder"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals("offers", order[order.indexOf("hero") + 1])
        assertFalse("offers" in toOffers["hidden"]!!.jsonArray.map { it.jsonPrimitive.content })

        assertEquals(draft, SiteContent.afterSwitch(draft, classic, modern), "personal to personal")
        assertEquals(draft, SiteContent.afterSwitch(draft, spotlight, guide), "offers to offers")
        assertEquals(draft, SiteContent.afterSwitch(draft, spotlight, classic), "switching to personal changes nothing")
    }
}
