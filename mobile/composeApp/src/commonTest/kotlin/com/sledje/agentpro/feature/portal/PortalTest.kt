package com.sledje.agentpro.feature.portal

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.feature.portal.data.InviteResult
import com.sledje.agentpro.feature.portal.data.PortalJson
import com.sledje.agentpro.feature.portal.data.PortalLinks
import com.sledje.agentpro.feature.portal.data.PortalRepository
import com.sledje.agentpro.feature.portal.data.PortalState
import com.sledje.agentpro.feature.portal.data.PortalStatus
import com.sledje.agentpro.feature.portal.ui.PortalNotice
import com.sledje.agentpro.feature.portal.ui.PortalViewModel
import com.sledje.agentpro.feature.portal.ui.WebsiteLoginUiState
import com.sledje.agentpro.testing.testCustomer
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakePortal : PortalRepository {
    var status: AppResult<PortalStatus> = Result.Ok(PortalStatus.None)
    var invite: AppResult<InviteResult> = Result.Ok(InviteResult.Created("tok123", Instant.parse("2026-10-30T10:00:00Z"), false))
    var revoke: AppResult<Unit> = Result.Ok(Unit)
    val calls = mutableListOf<String>()

    override suspend fun status(customerId: String): AppResult<PortalStatus> { calls += "status"; return status }
    override suspend fun invite(customerId: String): AppResult<InviteResult> { calls += "invite"; return invite }
    override suspend fun revoke(customerId: String): AppResult<Unit> { calls += "revoke"; return revoke }
}

class PortalJsonTest {
    private fun parse(json: String) = RowJson.parseToJsonElement(json)

    @Test
    fun statusActiveWithSharedNames() {
        val s = PortalJson.status(
            parse("""{"state":"active","invite_expires_at":null,"last_login_at":"2026-10-20T08:00:00+00:00","locked_until":null,"shared_with":["Asha","Ravi"]}"""),
        )!!
        assertEquals(PortalState.Active, s.state)
        assertEquals(Instant.parse("2026-10-20T08:00:00Z"), s.lastLoginAt)
        assertNull(s.inviteExpiresAt)
        assertEquals(listOf("Asha", "Ravi"), s.sharedWith)
    }

    @Test
    fun portalAvailableIsParsedAndDefaultsToTrue() {
        assertEquals(false, PortalJson.status(parse("""{"state":"none","portal_available":false}"""))!!.portalAvailable)
        assertEquals(true, PortalJson.status(parse("""{"state":"none","portal_available":true}"""))!!.portalAvailable)
        assertEquals(true, PortalJson.status(parse("""{"state":"none"}"""))!!.portalAvailable, "missing -> true")
        assertEquals(true, PortalJson.status(parse("""{"state":"none","portal_available":null}"""))!!.portalAvailable)
    }

    @Test
    fun statusInvitedLockedAndUnknown() {
        assertEquals(Instant.parse("2026-10-30T10:00:00Z"), PortalJson.status(parse("""{"state":"invited","invite_expires_at":"2026-10-30T10:00:00Z","shared_with":[]}"""))!!.inviteExpiresAt)
        assertEquals(Instant.parse("2026-10-23T10:00:00Z"), PortalJson.status(parse("""{"state":"locked","locked_until":"2026-10-23T10:00:00Z"}"""))!!.lockedUntil)
        assertEquals(PortalState.None, PortalJson.status(parse("""{"state":"weird"}"""))!!.state)
        assertNull(PortalJson.status(parse("[]")))
    }

    @Test
    fun inviteResults() {
        val ok = PortalJson.invite(parse("""{"ok":true,"token":"abc","expires_at":"2026-10-30T10:00:00Z","already_active":true}"""))
        assertEquals(InviteResult.Created("abc", Instant.parse("2026-10-30T10:00:00Z"), true), ok)
        assertEquals(InviteResult.InvalidPhone, PortalJson.invite(parse("""{"ok":false,"error":"invalid","field":"phone"}""")))
        assertEquals(InviteResult.NoSite, PortalJson.invite(parse("""{"ok":false,"error":"no_site"}""")))
        assertEquals(InviteResult.Unavailable, PortalJson.invite(parse("""{"ok":false,"error":"unavailable"}""")))
        assertNull(PortalJson.invite(parse("""{"ok":true}""")), "no token")
        assertNull(PortalJson.invite(parse("""{"ok":false,"error":"???"}""")))
    }

    @Test
    fun startUrl() {
        assertEquals("https://x.site/a/ramesh/my/start?t=tok", PortalLinks.startUrl("https://x.site/a/ramesh/", "tok"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PortalViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val customer = MutableStateFlow(testCustomer().copy(phone = "+919876543210"))
    private val site = MutableStateFlow<com.sledje.agentpro.feature.website.data.Site?>(testSite("ramesh", published = true))
    private val online = MutableStateFlow(true)

    private fun vm(portal: FakePortal) = PortalViewModel("c1", portal, customer, site, online, MutableStateFlow("Ramesh"), now = { Instant.parse("2026-10-23T10:00:00Z") })

    @Test
    fun loginOffIsCarriedOnReadyAndKeptAfterSendAndTurnOff() = runTest {
        val portal = FakePortal()
        portal.status = Result.Ok(PortalStatus.None.copy(portalAvailable = false))
        val vm = vm(portal)
        runCurrent()
        assertEquals(false, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).status.portalAvailable)
        vm.send() // still allowed
        runCurrent()
        assertTrue(portal.calls.contains("invite"))
        vm.turnOff()
        runCurrent()
        assertEquals(false, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).status.portalAvailable)
    }

    @Test
    fun loadsNoneAsReady() = runTest {
        val portal = FakePortal()
        val vm = vm(portal)
        assertEquals(WebsiteLoginUiState.Loading, vm.state.value)
        runCurrent()
        assertEquals(PortalState.None, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).status.state)
    }

    @Test
    fun statusFailureShowsErrorAndRetryReloads() = runTest {
        val portal = FakePortal().apply { status = Result.Err(AppError.Offline) }
        val vm = vm(portal)
        runCurrent()
        assertEquals(WebsiteLoginUiState.Error(AppError.Offline), vm.state.value)
        portal.status = Result.Ok(PortalStatus(PortalState.Locked))
        vm.load()
        runCurrent()
        assertEquals(PortalState.Locked, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).status.state)
    }

    @Test
    fun blockedStates() = runTest {
        val portal = FakePortal()
        customer.value = testCustomer().copy(phone = null)
        val vm = vm(portal)
        runCurrent()
        assertIs<WebsiteLoginUiState.NeedsPhone>(vm.state.value)
        customer.value = testCustomer().copy(phone = "+919876543210")
        site.value = testSite("ramesh", published = false)
        runCurrent()
        assertIs<WebsiteLoginUiState.NeedsSite>(vm.state.value)
    }

    @Test
    fun offlineIsReportedOnReady() = runTest {
        val vm = vm(FakePortal())
        runCurrent()
        online.value = false
        runCurrent()
        assertEquals(false, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).online)
    }

    @Test
    fun sendCreatesInviteSharesTheLinkAndShowsInvited() = runTest {
        val portal = FakePortal()
        val vm = vm(portal)
        runCurrent()
        portal.status = Result.Ok(PortalStatus(PortalState.Invited, inviteExpiresAt = Instant.parse("2026-10-30T10:00:00Z")))
        vm.send()
        runCurrent()
        val share = vm.share.value!!
        assertTrue(share.url.endsWith("/a/ramesh/my/start?t=tok123"), share.url)
        assertEquals("Ramesh", share.agentName)
        assertEquals(PortalState.Invited, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).status.state)
        vm.shareHandled()
        assertNull(vm.share.value)
    }

    @Test
    fun inviteFailuresShowANoticeAndNeverShare() = runTest {
        val portal = FakePortal()
        val vm = vm(portal)
        runCurrent()
        portal.invite = Result.Ok(InviteResult.InvalidPhone)
        vm.send(); runCurrent()
        assertEquals(PortalNotice.InvalidPhone, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).notice)
        portal.invite = Result.Err(AppError.Offline)
        vm.send(); runCurrent()
        assertEquals(PortalNotice.Failed(AppError.Offline), assertIs<WebsiteLoginUiState.Ready>(vm.state.value).notice)
        assertNull(vm.share.value)
    }

    @Test
    fun turnOffRevokesAndResetsToNone() = runTest {
        val portal = FakePortal().apply { status = Result.Ok(PortalStatus(PortalState.Active, sharedWith = listOf("Asha"))) }
        val vm = vm(portal)
        runCurrent()
        vm.turnOff(); runCurrent()
        assertEquals("revoke", portal.calls.last())
        assertEquals(PortalState.None, assertIs<WebsiteLoginUiState.Ready>(vm.state.value).status.state)
    }
}
