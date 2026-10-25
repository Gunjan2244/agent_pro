package com.sledje.agentpro.feature.home

import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.features.FeatureFlags
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.testCustomer
import com.sledje.agentpro.testing.testMembership
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun deps(
        flags: FeatureFlags = FeatureFlags.Defaults,
        companies: CompanyRepository = FakeCompanyRepository(),
        site: Boolean = false,
    ) = FakeAppDependencies(
        profiles = FakeProfileRepository(testProfile()),
        sites = FakeSiteRepository(if (site) testSite() else null),
        companies = companies as? FakeCompanyRepository ?: FakeCompanyRepository(),
        flags = flags,
    )

    private fun TestScope.vm(d: FakeAppDependencies): HomeViewModel = HomeViewModel(d).also { runCurrent() }

    @Test
    fun allItemsShowWhenAllFlagsAreOnInChecklistOrder() = runTest {
        val s = vm(deps()).state.value
        assertEquals(
            listOf(SetupItem.Profile, SetupItem.Companies, SetupItem.Publish, SetupItem.About, SetupItem.Share, SetupItem.FirstCustomer, SetupItem.CustomerLogin),
            s.items,
        )
    }

    @Test
    fun itemsOfDisabledFeaturesAreHiddenAndNotCounted() = runTest {
        val s = vm(deps(FeatureFlags(mapOf("offers" to false, "customerPortal" to false)))).state.value
        assertEquals(listOf(SetupItem.Profile, SetupItem.Publish, SetupItem.About, SetupItem.Share, SetupItem.FirstCustomer), s.items)
        val finished = s.copy(done = s.items.toSet())
        assertTrue(finished.setupComplete, "complete without the hidden items")
        assertEquals(5, finished.doneCount)
        assertFalse(finished.copy(done = finished.done - SetupItem.Share).setupComplete)
    }

    @Test
    fun companiesIsDoneWithAnActiveOrNotListedLink() = runTest {
        val approved = FakeCompanyRepository(listOf(testMembership("m1", "approved")))
        assertTrue(SetupItem.Companies in vm(deps(companies = approved)).state.value.done)
        val notListed = FakeCompanyRepository(listOf(testMembership("m2", "pending", null, suggestedName = "Gamma")))
        assertTrue(SetupItem.Companies in vm(deps(companies = notListed)).state.value.done)
    }

    @Test
    fun companiesStaysUndoneWithoutAnActiveLinkOrWhenTheCheckFails() = runTest {
        assertFalse(SetupItem.Companies in vm(deps()).state.value.done)
        val rejected = FakeCompanyRepository(listOf(testMembership("m1", "rejected")))
        assertFalse(SetupItem.Companies in vm(deps(companies = rejected)).state.value.done)
        val offline = FakeCompanyRepository(listOf(testMembership("m1", "approved"))).apply { error = AppError.Offline }
        assertFalse(SetupItem.Companies in vm(deps(companies = offline)).state.value.done)
    }

    @Test
    fun companiesIsCheckedOnceAndAThrowIsSwallowed() = runTest {
        val fake = FakeCompanyRepository(listOf(testMembership("m1", "approved")))
        vm(deps(companies = fake))
        assertEquals(1, fake.refreshes)
        val throwing = object : CompanyRepository by FakeCompanyRepository(listOf(testMembership("m1", "approved"))) {
            override suspend fun refresh(): com.sledje.agentpro.core.data.AppResult<Unit> = error("boom")
        }
        val d = FakeAppDependencies(profiles = FakeProfileRepository(testProfile()), companies = FakeCompanyRepository())
        // The throwing repository can't be injected into the typed fake; the swallow is exercised directly.
        val vm = HomeViewModel(object : com.sledje.agentpro.app.AppDependencies by d { override val companies = throwing })
        runCurrent()
        assertFalse(SetupItem.Companies in vm.state.value.done)
    }

    @Test
    fun customerLoginIsDoneOnceAnInviteWasSent() = runTest {
        val d = deps()
        val vm = vm(d)
        assertFalse(SetupItem.CustomerLogin in vm.state.value.done)
        d.store.put(StoreKeys.PORTAL_INVITED, "true")
        runCurrent()
        assertTrue(SetupItem.CustomerLogin in vm.state.value.done)
    }

    @Test
    fun firstCustomerIsDoneWhenCustomersExist() = runTest {
        val d = deps()
        val vm = vm(d)
        assertFalse(SetupItem.FirstCustomer in vm.state.value.done)
        d.customers.rows.value = listOf(testCustomer())
        runCurrent()
        assertTrue(SetupItem.FirstCustomer in vm.state.value.done)
    }

    @Test
    fun nextIsTheFirstUndoneVisibleItemAndEveryRowIsTappable() {
        val all = SetupItem.entries.toList()
        assertEquals(SetupItem.Profile, HomeUiState(done = emptySet()).next)
        assertEquals(SetupItem.Publish, HomeUiState(done = setOf(SetupItem.Profile, SetupItem.Companies)).next)
        // Share is done but About is not: About is next, and every row is tappable.
        val s = HomeUiState(done = setOf(SetupItem.Profile, SetupItem.Companies, SetupItem.Publish, SetupItem.Share))
        assertEquals(SetupItem.About, s.next)
        assertTrue(all.all(s::isTappable), "no step blocks the others")
        assertNull(HomeUiState(done = all.toSet()).next)
        assertTrue(all.all { HomeUiState(done = all.toSet()).isTappable(it) })
    }

    @Test
    fun nextSkipsHiddenItems() {
        val items = listOf(SetupItem.Profile, SetupItem.Publish)
        val s = HomeUiState(items = items, done = setOf(SetupItem.Profile))
        assertEquals(SetupItem.Publish, s.next)
        assertFalse(s.isTappable(SetupItem.Companies), "a hidden item is never tappable")
    }

    @Test
    fun whatsNewShowsRowsForEnabledFlagsToAgentsWithASiteWhoHaventSeenIt() = runTest {
        assertEquals(WhatsNewItem.entries.toList(), vm(deps(site = true)).state.value.whatsNew)
        assertEquals(listOf(WhatsNewItem.Companies), vm(deps(FeatureFlags(mapOf("customerPortal" to false)), site = true)).state.value.whatsNew)
        assertTrue(vm(deps(FeatureFlags(mapOf("offers" to false, "customerPortal" to false)), site = true)).state.value.whatsNew.isEmpty())
    }

    @Test
    fun whatsNewIsHiddenWithoutASiteAndOnceSeen() = runTest {
        assertTrue(vm(deps(site = false)).state.value.whatsNew.isEmpty())
        val d = deps(site = true)
        val vm = vm(d)
        assertTrue(vm.state.value.whatsNew.isNotEmpty())
        vm.markWhatsNewSeen()
        runCurrent()
        assertEquals("1", d.store.get(StoreKeys.WHATS_NEW_SEEN))
        assertTrue(vm.state.value.whatsNew.isEmpty())
    }

    @Test
    fun finishingOnboardingMarksWhatsNewSeen() = runTest {
        val d = deps(site = true)
        val session = OnboardingSession().apply { start() }
        val onboarding = OnboardingViewModel(
            d.profiles, d.sites, session, AppEvents(), FakeCompanyRepository(listOf(testMembership())), store = d.store,
        )
        runCurrent()
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        onboarding.later()
        runCurrent()
        assertTrue(onboarding.state.value.finished)
        assertEquals("1", d.store.get(StoreKeys.WHATS_NEW_SEEN))
        assertTrue(vm(d).state.value.whatsNew.isEmpty())
    }

    @Test
    fun whatsNewVersionRules() {
        assertTrue(WhatsNew.shouldShow(null, hasSite = true))
        assertTrue(WhatsNew.shouldShow("0", hasSite = true))
        assertFalse(WhatsNew.shouldShow("1", hasSite = true))
        assertFalse(WhatsNew.shouldShow("2", hasSite = true))
        assertFalse(WhatsNew.shouldShow(null, hasSite = false))
        assertTrue(WhatsNew.shouldShow("junk", hasSite = true))
    }
}
