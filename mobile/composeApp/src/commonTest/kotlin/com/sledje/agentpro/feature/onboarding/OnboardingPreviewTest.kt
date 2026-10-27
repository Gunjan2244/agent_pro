package com.sledje.agentpro.feature.onboarding

import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.testing.testMembership
import com.sledje.agentpro.testing.testProfile
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

/** Onboarding step 6, the finished website (docs/UX.md): the photo and every pending or approved company. */
class OnboardingPreviewTest {
    @Test
    fun companiesListPendingAndApprovedLinksButNotEndedOnes() {
        val list = StarterPreview.companies(
            listOf(
                testMembership(id = "m1", status = "pending", types = listOf("life", "health")),
                testMembership(id = "m2", status = "approved", company = null, suggestedName = " Gamma Life ", types = listOf("life")),
                testMembership(id = "m3", status = "rejected"),
                testMembership(id = "m4", status = "withdrawn"),
            ),
            locale = "hi",
        )
        assertEquals(listOf("अल्फा बीमा", "Gamma Life"), list.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertEquals(listOf("life", "health"), list[0].jsonObject["insurance_types"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun thePreviewCarriesThePhotoAndReplacesTheServersApprovedOnlyList() {
        val profile = testProfile().copy(photoUrl = "https://example.com/me.webp")
        val companies = StarterPreview.companies(listOf(testMembership(status = "pending")), "en")
        // preview_site_offers answers with approved companies only: none yet for a new agent.
        val server = buildJsonObject { put("companies", JsonArray(emptyList())) }
        val json = RowJson.decodeFromString(
            JsonObject.serializer(),
            StarterPreview.json(profile, Templates.all.first(), "en", previewOffers = server, companies = companies),
        )
        assertEquals("https://example.com/me.webp", json["profile"]!!.jsonObject["photo_url"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive("Alpha Insurance"), json["companies"]!!.jsonArray.single().jsonObject["name"])
    }
}
