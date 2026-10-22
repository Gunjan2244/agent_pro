package com.sledje.agentpro.feature.offers

import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.feature.offers.data.CompanyJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `list_companies()` and `my_company_offers()` JSON → models (docs/BACKEND.md, Companies and offers). */
class CompanyJsonTest {
    private fun parse(json: String) = RowJson.parseToJsonElement(json)

    @Test
    fun listCompaniesRows() {
        val list = CompanyJson.companies(
            parse("""[{"id":"c1","name":{"en":"Alpha Insurance","hi":"अल्फा बीमा"},"logo_url":null},{"id":"c2","name":{"en":"Beta"},"logo_url":"https://x/y.webp"}]"""),
        )
        assertEquals(listOf("c1", "c2"), list.map { it.id })
        assertEquals("Alpha Insurance", list[0].name("en"))
        assertEquals("अल्फा बीमा", list[0].name("hi"))
        assertNull(list[0].logoUrl)
        assertEquals("Beta", list[1].name("hi"), "falls back to English when the language is missing")
        assertEquals("https://x/y.webp", list[1].logoUrl)
    }

    @Test
    fun anythingThatIsNotAnArrayOrHasNoIdIsSkipped() {
        assertTrue(CompanyJson.companies(parse("{}")).isEmpty())
        assertEquals(1, CompanyJson.companies(parse("""[{"name":{"en":"No id"}},{"id":"c9","name":{"en":"Ok"}}]""")).size)
    }

    private val answer = parse(
        """
        {"memberships":[
          {"id":"m1","status":"approved","company":{"id":"c1","name":{"en":"Alpha Insurance","hi":"अल्फा बीमा"},"logo_url":null,"allow_custom_offers":true},
           "suggested_name":null,"agency_code":"AG123","insurance_types":["life","health"],"reject_reason":null,"review_note":null,
           "requested_at":"2026-10-16T08:00:00+00:00","reviewed_at":"2026-10-17T08:00:00+00:00"},
          {"id":"m2","status":"pending","company":null,"suggested_name":"Gamma Life","agency_code":null,"insurance_types":["motor"],
           "reject_reason":null,"review_note":null,"requested_at":"2026-10-18T08:00:00+00:00","reviewed_at":null},
          {"id":"m3","status":"rejected","company":{"id":"c2","name":{"en":"Beta"},"logo_url":null,"allow_custom_offers":false},
           "suggested_name":null,"agency_code":"B9","insurance_types":["life"],"reject_reason":"details_mismatch","review_note":"Code unknown",
           "requested_at":"2026-10-10T08:00:00+00:00","reviewed_at":"2026-10-11T08:00:00+00:00"},
          {"id":"m4","status":"withdrawn","company":null,"suggested_name":"Old","agency_code":null,"insurance_types":["life"],
           "reject_reason":null,"review_note":null,"requested_at":null,"reviewed_at":null}
        ],"offers":[{"id":"o1","company_id":"c1"}]}
        """.trimIndent(),
    ).jsonObject

    @Test
    fun membershipsFromMyCompanyOffers() {
        val list = CompanyJson.memberships(answer)
        assertEquals(listOf("m1", "m2", "m3"), list.map { it.id }, "withdrawn rows are left out; offers are not read here")
        val approved = list[0]
        assertEquals("approved", approved.status)
        assertEquals("Alpha Insurance", approved.displayName("en"))
        assertEquals("अल्फा बीमा", approved.displayName("hi"))
        assertEquals("AG123", approved.agencyCode)
        assertEquals(listOf("life", "health"), approved.insuranceTypes)
        assertTrue(approved.isActive)
        val notListed = list[1]
        assertNull(notListed.company)
        assertEquals("Gamma Life", notListed.displayName("hi"), "a company that isn't listed shows the typed name")
        assertNull(notListed.agencyCode)
        val rejected = list[2]
        assertEquals("details_mismatch", rejected.rejectReason)
        assertEquals("Code unknown", rejected.reviewNote)
        assertEquals(false, rejected.isActive)
    }

    @Test
    fun unknownTypeKeysAreKeptButNotListedAsCatalogTypes() {
        val m = CompanyJson.membership(
            parse("""{"id":"m","status":"pending","insurance_types":["life","spaceflight"]}"""),
        )!!
        assertEquals(listOf("life", "spaceflight"), m.insuranceTypes)
        assertEquals(listOf("life"), m.types.map { it.key })
    }

    @Test
    fun emptyOrMissingMembershipsGiveAnEmptyList() {
        assertTrue(CompanyJson.memberships(parse("{}").jsonObject).isEmpty())
        assertTrue(CompanyJson.memberships(parse("""{"memberships":[]}""").jsonObject).isEmpty())
        assertEquals(0, parse("""{"memberships":[]}""").jsonObject["memberships"]!!.jsonArray.size)
    }
}
