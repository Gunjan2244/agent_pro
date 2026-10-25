package com.sledje.agentpro.feature.offers

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.feature.offers.data.CompanyJson
import com.sledje.agentpro.feature.offers.data.OfferDraft
import com.sledje.agentpro.feature.offers.data.OfferRef
import com.sledje.agentpro.feature.offers.ui.OfferFlag
import com.sledje.agentpro.feature.offers.ui.OfferPickerViewModel
import com.sledje.agentpro.feature.offers.ui.PickerEvent
import com.sledje.agentpro.feature.offers.ui.PickerMode
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.testMembership
import com.sledje.agentpro.testing.testOffer
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfferPickerViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 10, 24)
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    private fun siteWith(content: String = "{}") = FakeSiteRepository(testSite().copy(draftContent = json(content)))
    private fun approved() = FakeCompanyRepository(listOf(testMembership(status = "approved"))).also {
        it.liveOffers = listOf(testOffer("o1"), testOffer("o2"), testOffer("o3"))
    }

    private fun TestScope.vm(sites: FakeSiteRepository, companies: FakeCompanyRepository, mode: PickerMode = PickerMode.Items, cap: Int = 6) =
        OfferPickerViewModel(mode, sites, companies, cap, { today }, { "new-id" }).also { runCurrent() }

    private fun TestScope.saved(sites: FakeSiteRepository): JsonObject = sites.drafts.last()

    // ---- JSON mapping ---------------------------------------------------------------------

    @Test
    fun theLiveOffersAnswerBecomesModels() {
        val answer = json(
            """{"memberships":[{"id":"m1","status":"approved","company":{"id":"c1","name":{"en":"Alpha","hi":"अल्फा"}},"insurance_types":["life"]}],
            "offers":[{"id":"o1","company_id":"c1","content":{"en":{"title":"Safe","summary":"S"},"hi":{"title":"सुरक्षित"}},"image_url":null,
            "cta_kind":"enquire","cta_url":null,"interest":"term","starts_at":"2026-10-01T00:00:00Z","ends_at":"2026-12-31T18:29:59Z","phase":"running"},{"company_id":"c1"}]}""",
        )
        val live = CompanyJson.liveOffers(answer)
        assertEquals(1, live.offers.size, "a row without an id is skipped")
        val o = live.offers.single()
        assertEquals("Safe", o.title("en"))
        assertEquals("सुरक्षित", o.title("hi"))
        assertEquals("S", o.summary("hi"), "a locale without a summary falls back to English")
        assertEquals("term", o.interest)
        assertEquals("running", o.phase)
        assertEquals("Alpha", live.companyName(o, "en"))
        assertEquals(1, live.approved.size)
    }

    // ---- States -----------------------------------------------------------------------------

    @Test
    fun offlineShowsTheErrorAndKeepsTheSitesOffers() = runTest {
        val sites = siteWith("""{"offers":{"items":[{"source":"company","id":"o1"}]}}""")
        val companies = approved().also { it.liveError = AppError.Offline }
        val vm = vm(sites, companies)
        assertEquals(AppError.Offline, vm.state.value.loadError)
        assertEquals(1, vm.state.value.onSite.size)
        assertNull(vm.state.value.onSite.single().flag, "unknown, not flagged, while offline")
        companies.liveError = null
        vm.load(); runCurrent()
        assertNull(vm.state.value.loadError)
        assertEquals("Title o1", vm.state.value.onSite.single().title)
    }

    @Test
    fun noApprovedCompanyIsItsOwnState() = runTest {
        val vm = vm(siteWith(), FakeCompanyRepository(listOf(testMembership(status = "pending"))))
        assertTrue(vm.state.value.noCompany)
        assertFalse(vm.state.value.loading)
        assertEquals(emptyList(), vm.state.value.available)
    }

    // ---- Add, cap, remove, move ------------------------------------------------------------

    @Test
    fun addAppendsACompanyRefAndSavesAtOnce() = runTest {
        val sites = siteWith("""{"hero":{"headline":"Hi"}}""")
        val vm = vm(sites, approved())
        assertEquals(listOf("o1", "o2", "o3"), vm.state.value.available.map { it.ref.id })
        vm.add(OfferRef.company("o2")); runCurrent()
        vm.add(OfferRef.company("o1")); runCurrent()
        assertEquals(json("""{"hero":{"headline":"Hi"},"offers":{"items":[{"source":"company","id":"o2"},{"source":"company","id":"o1"}]}}"""), saved(sites))
        assertEquals(listOf("o2", "o1"), vm.state.value.onSite.map { it.ref.id })
        assertEquals(listOf("o3"), vm.state.value.available.map { it.ref.id }, "added offers leave section B")
    }

    @Test
    fun theCapStopsFurtherAdds() = runTest {
        val sites = siteWith()
        val vm = vm(sites, approved(), cap = 2)
        vm.add(OfferRef.company("o1")); runCurrent()
        vm.add(OfferRef.company("o2")); runCurrent()
        assertTrue(vm.state.value.full)
        vm.add(OfferRef.company("o3")); runCurrent()
        assertEquals(2, OfferDraft.items(sites.site.value!!.draftContent).size)
        assertEquals(2, sites.drafts.size, "the refused add saved nothing")
    }

    @Test
    fun removeDropsTheRefAndClearsTheKeyWhenEmpty() = runTest {
        val sites = siteWith("""{"offers":{"title":"Deals","items":[{"source":"company","id":"o1"}]}}""")
        val vm = vm(sites, approved())
        vm.remove(OfferRef.company("o1")); runCurrent()
        assertEquals(json("""{"offers":{"title":"Deals"}}"""), saved(sites), "no items = the automatic offers (DECISIONS #79)")
    }

    @Test
    fun moveReordersWithinTheList() = runTest {
        val sites = siteWith("""{"offers":{"items":[{"source":"company","id":"o1"},{"source":"company","id":"o2"},{"source":"company","id":"o3"}]}}""")
        val vm = vm(sites, approved())
        vm.move(2, -1); runCurrent()
        assertEquals(listOf("o1", "o3", "o2"), vm.state.value.onSite.map { it.ref.id })
        vm.move(0, -1); runCurrent()
        assertEquals(1, sites.drafts.size, "moving the first row up changes nothing")
    }

    // ---- Featured ---------------------------------------------------------------------------

    @Test
    fun setFeaturedWritesTheHeroRef() = runTest {
        val sites = siteWith("""{"hero":{"headline":"Hi"},"offers":{"items":[{"source":"company","id":"o1"}]}}""")
        val vm = vm(sites, approved())
        vm.setFeatured(OfferRef.company("o1")); runCurrent()
        assertEquals(json("""{"source":"company","id":"o1"}"""), saved(sites)["hero"]!!.jsonObject["featuredOffer"])
        assertTrue(vm.state.value.onSite.single().featured)
        vm.setFeatured(null); runCurrent()
        assertEquals(json("""{"headline":"Hi"}"""), saved(sites)["hero"])
    }

    @Test
    fun theHeroPickerChoosesNoneCompanyOrOwnAndCloses() = runTest {
        val sites = siteWith("""{"offers":{"custom":[{"id":"u1","title":"Mine","endsOn":"2026-11-30"}],"items":[{"source":"agent","id":"u1"}]}}""")
        val vm = vm(sites, approved(), PickerMode.Featured)
        vm.setFeatured(OfferRef.company("o3")); runCurrent()
        assertEquals(json("""{"source":"company","id":"o3"}"""), saved(sites)["hero"]!!.jsonObject["featuredOffer"])
        assertEquals(PickerEvent.Done, vm.state.value.event)
        vm.consumeEvent()
        vm.setFeatured(OfferRef.agent("u1")); runCurrent()
        assertEquals(json("""{"source":"agent","id":"u1"}"""), saved(sites)["hero"]!!.jsonObject["featuredOffer"])
        vm.setFeatured(null); runCurrent()
        assertNull(saved(sites)["hero"]!!.jsonObject["featuredOffer"])
        assertEquals(listOf(OfferRef.agent("u1")), OfferDraft.items(saved(sites)))
    }

    @Test
    fun removingTheFeaturedOfferUnfeaturesIt() = runTest {
        val sites = siteWith("""{"hero":{"featuredOffer":{"source":"company","id":"o1"}},"offers":{"items":[{"source":"company","id":"o1"}]}}""")
        val vm = vm(sites, approved())
        vm.remove(OfferRef.company("o1")); runCurrent()
        assertNull(saved(sites)["hero"]!!.jsonObject["featuredOffer"])
    }

    // ---- Flags -------------------------------------------------------------------------------

    @Test
    fun endedAndUnavailablePicksAreFlaggedAndCanBeRemoved() = runTest {
        val sites = siteWith(
            """{"offers":{"custom":[{"id":"u1","title":"Old own","endsOn":"2026-10-01"}],
            "items":[{"source":"company","id":"o1"},{"source":"company","id":"gone"},{"source":"agent","id":"u1"},{"source":"agent","id":"missing"}]}}""",
        )
        val companies = approved().also { it.liveOffers = listOf(testOffer("o1"), testOffer("o1late", endsAt = "2026-10-20T18:29:59Z")) }
        val vm = vm(sites, companies)
        assertEquals(listOf(null, OfferFlag.Unavailable, OfferFlag.Ended, OfferFlag.Unavailable), vm.state.value.onSite.map { it.flag })
        vm.remove(OfferRef.company("gone")); runCurrent()
        assertEquals(3, vm.state.value.onSite.size)
        assertTrue(vm.state.value.available.none { it.ref.id == "o1late" }, "an ended company offer isn't offered")
    }

    // ---- Edit: the own copy -----------------------------------------------------------------

    @Test
    fun editingAnOwnOfferJustOpensTheEditor() = runTest {
        val sites = siteWith("""{"offers":{"custom":[{"id":"u1","title":"Mine","endsOn":"2026-11-30"}],"items":[{"source":"agent","id":"u1"}]}}""")
        val vm = vm(sites, approved())
        vm.edit(OfferRef.agent("u1"))
        assertEquals(PickerEvent.OpenEditor(copied = false), vm.state.value.event)
        assertTrue(sites.drafts.isEmpty())
    }

    @Test
    fun editingACompanyOfferAsksThenCopiesItWithTheExactShape() = runTest {
        val sites = siteWith(
            """{"hero":{"featuredOffer":{"source":"company","id":"o2"}},
            "offers":{"items":[{"source":"company","id":"o1"},{"source":"company","id":"o2"},{"source":"company","id":"o3"}]}}""",
        )
        val vm = vm(sites, approved())
        vm.edit(OfferRef.company("o2"))
        assertEquals(OfferRef.company("o2"), vm.state.value.confirmCopy)
        assertTrue(sites.drafts.isEmpty(), "nothing changes before the agent confirms")
        vm.confirmCopy(); runCurrent()
        val out = saved(sites)
        assertEquals(
            json(
                """{"id":"new-id","title":"Title o2","summary":"Summary o2","endsOn":"2026-12-31","ctaKind":"enquire","interest":"term","confirmed":false}""",
            ),
            out["offers"]!!.jsonObject["custom"]!!.jsonArray.single(),
        )
        assertEquals(
            Json.parseToJsonElement("""[{"source":"company","id":"o1"},{"source":"agent","id":"new-id"},{"source":"company","id":"o3"}]"""),
            out["offers"]!!.jsonObject["items"],
        )
        assertEquals(json("""{"source":"agent","id":"new-id"}"""), out["hero"]!!.jsonObject["featuredOffer"], "the featured offer is re-pointed")
        assertEquals(PickerEvent.OpenEditor(copied = true), vm.state.value.event)
        assertNull(vm.state.value.confirmCopy)
    }

    @Test
    fun theCopysEndDateIsCappedAtTodayPlus366Days() = runTest {
        val sites = siteWith("""{"offers":{"items":[{"source":"company","id":"o1"}]}}""")
        val companies = approved().also { it.liveOffers = listOf(testOffer("o1", endsAt = "2029-01-01T18:29:59Z")) }
        val vm = vm(sites, companies)
        vm.edit(OfferRef.company("o1")); vm.confirmCopy(); runCurrent()
        assertEquals("2027-10-25", saved(sites)["offers"]!!.jsonObject["custom"]!!.jsonArray.single().jsonObject["endsOn"]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun cancellingTheCopyChangesNothing() = runTest {
        val sites = siteWith("""{"offers":{"items":[{"source":"company","id":"o1"}]}}""")
        val vm = vm(sites, approved())
        vm.edit(OfferRef.company("o1")); vm.cancelCopy(); runCurrent()
        assertNull(vm.state.value.confirmCopy)
        assertTrue(sites.drafts.isEmpty())
    }

    @Test
    fun aFullOwnOffersListRefusesTheCopy() = runTest {
        val custom = (1..6).joinToString(",") { """{"id":"u$it","title":"T","endsOn":"2026-11-30"}""" }
        val sites = siteWith("""{"offers":{"custom":[$custom],"items":[{"source":"company","id":"o1"}]}}""")
        val vm = vm(sites, approved())
        vm.edit(OfferRef.company("o1")); vm.confirmCopy(); runCurrent()
        assertTrue(sites.drafts.isEmpty())
        assertEquals(AppError.Validation("offers.custom", "max_items"), vm.state.value.error)
    }

    @Test
    fun aFailedSaveShowsTheError() = runTest {
        val sites = siteWith().also { it.writeError = AppError.Offline }
        val vm = vm(sites, approved())
        vm.add(OfferRef.company("o1")); runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertFalse(vm.state.value.saving)
    }
}
