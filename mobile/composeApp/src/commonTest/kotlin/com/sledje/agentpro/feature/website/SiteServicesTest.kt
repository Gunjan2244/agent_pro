package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.SiteSeed
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.feature.offers.ui.CompaniesViewModel
import com.sledje.agentpro.feature.offers.ui.CompanySheet
import com.sledje.agentpro.feature.website.data.ServicesUpdate
import com.sledje.agentpro.feature.website.data.SiteServices
import com.sledje.agentpro.feature.website.data.StarterContent
import com.sledje.agentpro.feature.website.ui.ServicesMatchViewModel
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.testCompany
import com.sledje.agentpro.testing.testMembership
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DECISIONS #93: the agent's types, the services diff and its update, and "Update your website to match?". */
@OptIn(ExperimentalCoroutinesApi::class)
class SiteServicesTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun content(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun JsonObject.offered() = this["services"]?.jsonObject?.get("offered")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    private fun JsonObject.items() = this["services"]?.jsonObject?.get("items")?.jsonArray?.map { it.jsonObject }.orEmpty()
    private fun JsonObject.itemInterests() = items().map { it["interest"]!!.jsonPrimitive.content }

    // ---- The catalog and the seed ---------------------------------------------------------------------------------

    @Test
    fun everyInterestHasAnInsuranceTypeOrNone() {
        assertEquals(setOf("term", "endowment", "money_back", "child", "pension", "ulip"), Interest.entries.filter { it.insuranceType == "life" }.map { it.key }.toSet())
        assertEquals("health", Interest.Health.insuranceType)
        listOf("motor", "home", "travel", "business", "accident").forEach { assertEquals(it, Interest.fromKey(it)?.insuranceType, it) }
        assertNull(Interest.Other.insuranceType, "other is available for every type")
        assertEquals(Interest.Other, Interest.active.last(), "other stays last")
        SiteSeed.INSURANCE_TYPES.forEach { t -> assertTrue(SiteSeed.seedInterests[t].orEmpty().isNotEmpty(), t) }
    }

    @Test
    fun theCopyMixIsLifeOnlyWhenEveryTypeIsLife() {
        assertEquals("life", SiteSeed.mixFor(emptyList()), "no types → WHEN_NONE (life)")
        assertEquals("life", SiteSeed.mixFor(listOf("life")))
        assertEquals("general", SiteSeed.mixFor(listOf("health")))
        assertEquals("general", SiteSeed.mixFor(listOf("life", "motor")))
        assertEquals("life", SiteSeed.mixFor(listOf("bogus")), "unknown keys drop out")
    }

    // ---- The agent's types ----------------------------------------------------------------------------------------

    @Test
    fun theAgentsTypesAreTheUnionOverPendingAndApprovedLinks() {
        val links = listOf(
            testMembership("m1", status = "approved", types = listOf("motor", "life")),
            testMembership("m2", status = "pending", company = testCompany("c2"), types = listOf("health", "life")),
            testMembership("m3", status = "rejected", company = testCompany("c3"), types = listOf("travel")),
            testMembership("m4", status = "revoked", company = testCompany("c4"), types = listOf("home")),
        )
        assertEquals(listOf("life", "health", "motor"), SiteServices.agentTypes(links), "catalog order; rejected and revoked don't count")
        assertEquals(emptyList(), SiteServices.agentTypes(emptyList()))
    }

    // ---- The diff -------------------------------------------------------------------------------------------------

    @Test
    fun aSeededSiteMatchesItsTypes() {
        for (types in listOf(listOf("life"), listOf("motor"), listOf("life", "health", "travel"))) {
            val c = StarterContent.from(testProfile(), Templates.all.first(), "en", "a1", types)
            assertTrue(SiteServices.diff(c, types).isEmpty, "$types")
        }
    }

    @Test
    fun theDiffAddsServicesForNewTypesAndOffersToRemoveOldOnes() {
        val life = StarterContent.from(testProfile(), Templates.all.first(), "en", "a1", listOf("life"))
        assertEquals(ServicesUpdate(add = listOf("motor"), remove = emptyList()), SiteServices.diff(life, listOf("life", "motor")))
        assertEquals(ServicesUpdate(add = listOf("motor"), remove = listOf("term", "endowment", "child", "pension")), SiteServices.diff(life, listOf("motor")))
        // An old site (before DECISIONS #93): life plans plus health, for a motor-only agent.
        val old = content("""{"services":{"offered":["term","health","other"],"items":[{"interest":"term","description":"Mine"}]}}""")
        assertEquals(ServicesUpdate(add = listOf("motor"), remove = listOf("term", "health")), SiteServices.diff(old, listOf("motor")), "other never counts")
        // A type that still has a service isn't "missing", even if the agent removed some of its seeded interests.
        assertTrue(SiteServices.diff(content("""{"services":{"offered":["term"]}}"""), listOf("life")).isEmpty)
        assertEquals(ServicesUpdate(listOf("term", "endowment", "child", "pension"), emptyList()), SiteServices.diff(JsonObject(emptyMap()), listOf("life")))
        assertTrue(SiteServices.diff(old, emptyList()).isEmpty, "nothing declared, nothing to compare")
    }

    // ---- The update -----------------------------------------------------------------------------------------------

    @Test
    fun applyingAddsSeededItemsAndRemovesOnlyTheChosenOnes() {
        val old = content("""{"hero":{"headline":"My own words"},"services":{"offered":["term","health","other"],"items":[{"interest":"term","description":"Mine"},{"interest":"health"}]}}""")
        val updated = SiteServices.apply(old, add = listOf("motor", "travel"), remove = listOf("health"), profile = testProfile(), locale = "hi", agentId = "a1")
        assertEquals(listOf("term", "other", "motor", "travel"), updated.offered())
        assertEquals(listOf("term", "motor", "travel"), updated.itemInterests())
        assertEquals("Mine", updated.items()[0]["description"]!!.jsonPrimitive.content, "the agent's own description is kept")
        assertEquals(StarterContent.serviceDescription("motor", 1, testProfile(), "hi", "a1"), updated.items()[1]["description"]!!.jsonPrimitive.content, "seeded in the site's language")
        assertEquals(old["hero"], updated["hero"], "no other text changes")
        val kept = SiteServices.apply(old, add = emptyList(), remove = emptyList(), profile = null, locale = "en", agentId = "a1")
        assertEquals(old, kept)
    }

    @Test
    fun applyingNeverGrowsTheItemsPastMaxItems() {
        val full = content("""{"services":{"items":[${(1..8).joinToString(",") { """{"interest":"term"}""" }}]}}""")
        val updated = SiteServices.apply(full, add = listOf("motor"), remove = emptyList(), profile = null, locale = "en", agentId = "a1")
        assertEquals(8, updated.items().size)
        assertEquals(listOf("motor"), updated.offered(), "still offered")
    }

    // ---- ServicesMatchViewModel -----------------------------------------------------------------------------------

    private fun lifeSite() = testSite().copy(draftContent = StarterContent.from(testProfile(), Templates.all.first(), "en", "a1", listOf("life")), locale = "en")

    @Test
    fun aTypesChangeOffersTheUpdateOnceAndAppliesOnlyWhatTheAgentChose() = runTest {
        val sites = FakeSiteRepository(lifeSite())
        val repo = FakeCompanyRepository(listOf(testMembership(status = "approved", types = listOf("life"))))
        val match = ServicesMatchViewModel(sites, repo, FakeProfileRepository(testProfile()))
        val companies = CompaniesViewModel(repo, onChanged = match::typesChanged)
        runCurrent()
        assertFalse(match.state.value.mismatch)
        companies.editTypes(repo.memberships.value.single())
        (companies.state.value.sheet as CompanySheet.Form).form.set(CompaniesViewModel.TYPES, listOf("motor"))
        companies.submit()
        runCurrent()
        val s = match.state.value
        assertTrue(s.sheetOpen, "offered right after the change")
        assertEquals(listOf("motor"), s.update.add)
        assertEquals(listOf("term", "endowment", "child", "pension"), s.update.remove)
        assertEquals(emptyList(), match.chosenRemovals(), "removal is opt-in")
        s.removeForm!!.set(ServicesMatchViewModel.REMOVE, listOf("child"))
        match.apply()
        runCurrent()
        val draft = sites.drafts.single()
        assertEquals(listOf("term", "endowment", "pension", "motor"), draft.offered())
        assertTrue(match.state.value.applied)
        assertFalse(match.state.value.sheetOpen)
        assertEquals(listOf("term", "endowment", "pension"), match.state.value.update.remove, "the hint stays for what was kept")
    }

    @Test
    fun notNowClosesTheSheetWithoutSavingAndNoPromptWhenNothingChanged() = runTest {
        val sites = FakeSiteRepository(lifeSite())
        val repo = FakeCompanyRepository(listOf(testMembership(status = "approved", types = listOf("life", "health"))))
        val match = ServicesMatchViewModel(sites, repo, FakeProfileRepository(testProfile()), refreshCompanies = true)
        runCurrent()
        assertEquals(1, repo.refreshes, "Website → Content loads the links")
        assertTrue(match.state.value.mismatch, "health has no service yet: the Content hint shows")
        match.open()
        assertTrue(match.state.value.sheetOpen)
        assertNull(match.state.value.removeForm, "nothing to remove")
        match.dismiss()
        assertFalse(match.state.value.sheetOpen)
        assertTrue(sites.drafts.isEmpty())
        // A change that keeps the site matching opens nothing.
        val ok = ServicesMatchViewModel(sites, FakeCompanyRepository(listOf(testMembership(status = "approved", types = listOf("life")))), FakeProfileRepository(testProfile()))
        runCurrent()
        ok.typesChanged()
        assertFalse(ok.state.value.sheetOpen)
        // No site yet: never a prompt.
        val none = ServicesMatchViewModel(FakeSiteRepository(null), repo, FakeProfileRepository(testProfile()))
        runCurrent()
        none.typesChanged()
        assertFalse(none.state.value.sheetOpen)
    }

    @Test
    fun aFailedSaveKeepsTheSheetOpenWithTheError() = runTest {
        val sites = FakeSiteRepository(lifeSite()).apply { writeError = com.sledje.agentpro.core.data.AppError.Offline }
        val repo = FakeCompanyRepository(listOf(testMembership(status = "approved", types = listOf("life", "motor"))))
        val match = ServicesMatchViewModel(sites, repo, FakeProfileRepository(testProfile()))
        runCurrent()
        match.open()
        match.apply()
        runCurrent()
        assertTrue(match.state.value.sheetOpen)
        assertEquals(com.sledje.agentpro.core.data.AppError.Offline, match.state.value.error)
    }
}
