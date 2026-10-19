package com.sledje.agentpro.app

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The gate on a start with an empty cache (fresh install, reinstall, sign-in after sign-out): an
 * agent who already has a site must reach Main, never onboarding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGateStartupTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /**
     * Like Room: a fresh query sees [written] at once, but the live query re-reads it only on
     * [notifyLive], because Room's invalidation arrives a moment after the write.
     */
    private class RoomLikeSites(val server: FakeSiteRepository = FakeSiteRepository()) : SiteRepository by server {
        val written = MutableStateFlow<Site?>(null)
        private val invalidations = MutableStateFlow(0)

        fun notifyLive() { invalidations.value++ }

        override fun observeMine(): Flow<Site?> = invalidations.map { written.value }

        /** Asking the server caches what it returns, like the real repository. */
        override suspend fun fetchMine(): AppResult<Site?> =
            server.fetchMine().also { if (it is Result.Ok) written.value = it.value }
    }

    private fun depsWith(roomSites: RoomLikeSites, fake: FakeAppDependencies): AppDependencies =
        object : AppDependencies by fake {
            override val sites: SiteRepository = roomSites
        }

    @Test
    fun siteWrittenBeforeTheLiveQueryCatchesUpOpensMain() = runTest {
        val roomSites = RoomLikeSites()
        val fake = FakeAppDependencies(auth = FakeAuthRepository(AuthState.Loading), profiles = FakeProfileRepository(testProfile()))
        // The sync writes the site and marks itself Done; the live query hasn't re-emitted yet.
        fake.dataSync.onRefresh = { roomSites.written.value = testSite() }
        val deps = depsWith(roomSites, fake)
        val root = RootViewModel(deps)
        testScheduler.advanceUntilIdle()

        fake.auth.state.value = AuthState.SignedIn(TEST_USER, null)
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Ready, root.gate.value)
        assertFalse(deps.onboarding.active.value, "onboarding must not be latched")

        roomSites.notifyLive()
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Ready, root.gate.value)
    }

    @Test
    fun noSiteAfterASuccessfulSyncStillOpensOnboarding() = runTest {
        val roomSites = RoomLikeSites()
        val fake = FakeAppDependencies(auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, null)), profiles = FakeProfileRepository(testProfile()))
        val deps = depsWith(roomSites, fake)
        val root = RootViewModel(deps)
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Onboarding, root.gate.value)
        assertEquals(1, roomSites.server.fetches, "the server was asked before onboarding started")
    }

    @Test
    fun siteOnlyOnTheServerOpensMain() = runTest {
        // The cache was emptied (e.g. by an app update) but the sync cursors survived, so the sync
        // brings nothing back: the server still has the site.
        val roomSites = RoomLikeSites()
        roomSites.server.serverSite = testSite("gunjan23")
        val fake = FakeAppDependencies(auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, null)), profiles = FakeProfileRepository(testProfile()))
        val deps = depsWith(roomSites, fake)
        val root = RootViewModel(deps)
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Ready, root.gate.value)
        assertFalse(deps.onboarding.active.value)
        assertEquals("gunjan23", roomSites.written.value?.slug, "the server's site is cached")
    }

    @Test
    fun serverUnreachableShowsRetryNotOnboarding() = runTest {
        val roomSites = RoomLikeSites()
        roomSites.server.fetchError = AppError.Offline
        val fake = FakeAppDependencies(auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, null)), profiles = FakeProfileRepository(testProfile()))
        val deps = depsWith(roomSites, fake)
        val root = RootViewModel(deps)
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Error, root.gate.value)
        assertFalse(deps.onboarding.active.value)
    }

    @Test
    fun failedSyncWithNoCachedSiteShowsRetryNotOnboarding() = runTest {
        val roomSites = RoomLikeSites()
        val fake = FakeAppDependencies(auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, null)), profiles = FakeProfileRepository(testProfile()))
        fake.dataSync.result = Result.Err(AppError.Offline)
        val deps = depsWith(roomSites, fake)
        val root = RootViewModel(deps)
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Error, root.gate.value)
        assertFalse(deps.onboarding.active.value)

        // Retry once back online: the site syncs and Main opens.
        fake.dataSync.result = Result.Ok(Unit)
        fake.dataSync.onRefresh = { roomSites.written.value = testSite() }
        root.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(AppGate.Ready, root.gate.value)
    }
}
