package com.sledje.agentpro.feature.auth

import app.cash.turbine.test
import com.sledje.agentpro.app.AppMessage
import com.sledje.agentpro.app.RootViewModel
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.auth.data.AuthCallbackLink
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.auth.data.SupabaseAuthRepository
import com.sledje.agentpro.feature.auth.ui.AuthFormError
import com.sledje.agentpro.feature.auth.ui.RecoveryStep
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordUiState
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordViewModel
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Password reset → Set new password (docs/UX.md; DECISIONS #46). Fakes only: no Supabase call. */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryTest {
    private val auth = FakeAuthRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // --- AuthCallbackLink -------------------------------------------------------------------------

    @Test
    fun recoveryLinkWithASession() {
        val link = AuthCallbackLink.parse(
            "agentpro://auth-callback?type=recovery#access_token=at&expires_in=3600&refresh_token=rt&token_type=bearer&type=recovery",
        )
        assertTrue(link.recovery)
        assertTrue(link.hasSession)
        assertNull(link.error)
        assertTrue(link.sessionFragment!!.startsWith("access_token=at"))
    }

    @Test
    fun expiredRecoveryLinkKeepsTheMarker() {
        val link = AuthCallbackLink.parse(
            "agentpro://auth-callback?type=recovery#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired",
        )
        assertTrue(link.recovery)
        assertFalse(link.hasSession)
        assertEquals("otp_expired", link.error)
    }

    @Test
    fun errorInTheQueryIsSeenToo() {
        val link = AuthCallbackLink.parse("agentpro://auth-callback?type=recovery&error=access_denied&error_code=otp_expired")
        assertEquals("otp_expired", link.error)
    }

    @Test
    fun confirmationLinkIsNotRecovery() {
        val implicit = AuthCallbackLink.parse("agentpro://auth-callback#access_token=at&refresh_token=rt&type=signup")
        assertFalse(implicit.recovery)
        assertTrue(implicit.hasSession)
        val pkce = AuthCallbackLink.parse("agentpro://auth-callback?code=abc123")
        assertEquals("abc123", pkce.code)
        assertFalse(pkce.recovery)
        assertFalse(AuthCallbackLink.parse("agentpro://auth-callback").hasSession)
    }

    @Test
    fun resetRedirectIsTheAuthCallbackWithTheMarker() {
        assertTrue(AuthCallbackLink.RECOVERY_REDIRECT.startsWith("agentpro://auth-callback"))
        assertTrue(AuthCallbackLink.parse(AuthCallbackLink.RECOVERY_REDIRECT).recovery)
    }

    @Test
    fun expiredOrEmptyLinksAreRefusedWithoutAServerCall() = runTest {
        // Not configured on purpose: a call to Supabase would fail differently (NotConfigured → Unknown).
        val repo = SupabaseAuthRepository(
            com.sledje.agentpro.core.data.SupabaseProvider(url = "", anonKey = ""),
            com.sledje.agentpro.core.data.InMemoryKeyValueStore(),
            backgroundScope,
            onSignedOut = {},
        )
        assertEquals(Result.Err(SupabaseAuthRepository.LINK_EXPIRED), repo.handleAuthCallback("agentpro://auth-callback?type=recovery#error_code=otp_expired"))
        assertEquals(Result.Err(SupabaseAuthRepository.LINK_EXPIRED), repo.handleAuthCallback("agentpro://auth-callback"))
    }

    // --- SetNewPasswordViewModel --------------------------------------------------------------------

    @Test
    fun passwordUnderEightCharactersIsNotSent() = runTest {
        val vm = SetNewPasswordViewModel(auth)
        vm.form.set(SetNewPasswordViewModel.PASSWORD, "short7c")
        assertFalse(vm.form.isValid)
        vm.submit()
        testScheduler.advanceUntilIdle()
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun savingUpdatesThePassword() = runTest {
        val vm = SetNewPasswordViewModel(auth)
        vm.form.set(SetNewPasswordViewModel.PASSWORD, "new-secret-1")
        vm.state.test {
            assertEquals(SetNewPasswordUiState(), awaitItem())
            vm.submit()
            assertTrue(awaitItem().submitting)
            val done = awaitItem()
            assertTrue(done.saved)
            assertFalse(done.submitting)
            assertNull(done.error)
        }
        assertEquals(listOf("updatePassword"), auth.calls)
        vm.submit()
        testScheduler.advanceUntilIdle()
        assertEquals(1, auth.calls.size, "saved once")
    }

    @Test
    fun weakAndSamePasswordsShowHumanErrors() = runTest {
        auth.updatePasswordResult = Result.Err(AppError.Validation("password", "weak"))
        val vm = SetNewPasswordViewModel(auth)
        vm.form.set(SetNewPasswordViewModel.PASSWORD, "password")
        vm.submit()
        testScheduler.advanceUntilIdle()
        assertEquals(AuthFormError.WeakPassword, vm.state.value.error)

        auth.updatePasswordResult = Result.Err(AppError.Validation("password", "same"))
        vm.submit()
        testScheduler.advanceUntilIdle()
        assertEquals(AuthFormError.SamePassword, vm.state.value.error)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun anExpiredRecoverySessionShowsLinkExpired() = runTest {
        auth.updatePasswordResult = Result.Err(AppError.Unauthorized)
        val vm = SetNewPasswordViewModel(auth)
        vm.form.set(SetNewPasswordViewModel.PASSWORD, "new-secret-1")
        vm.submit()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.expired)
        assertNull(vm.state.value.error)
    }

    @Test
    fun offlineShowsTheOfflineError() = runTest {
        auth.updatePasswordResult = Result.Err(AppError.Offline)
        val vm = SetNewPasswordViewModel(auth)
        vm.form.set(SetNewPasswordViewModel.PASSWORD, "new-secret-1")
        vm.submit()
        testScheduler.advanceUntilIdle()
        assertEquals(AuthFormError.Other(AppError.Offline), vm.state.value.error)
    }

    // --- RootViewModel: which links open the flow ---------------------------------------------------

    @Test
    fun recoveryLinkOpensSetNewPassword() = runTest {
        val deps = FakeAppDependencies()
        val root = RootViewModel(deps)
        root.handleAuthCallback("agentpro://auth-callback?type=recovery#access_token=at&refresh_token=rt&type=recovery")
        assertEquals(RecoveryStep.Verifying, root.recovery.value, "Splash while the session is imported")
        testScheduler.advanceUntilIdle()
        assertEquals(RecoveryStep.SetPassword, root.recovery.value)
        assertIs<AuthState.SignedIn>(deps.auth.state.value)

        root.onPasswordSaved()
        assertNull(root.recovery.value)
        assertEquals(AppMessage.PasswordUpdated, deps.events.message.value)
    }

    @Test
    fun expiredRecoveryLinkOpensLinkExpired() = runTest {
        val deps = FakeAppDependencies()
        deps.auth.callbackResult = Result.Err(SupabaseAuthRepository.LINK_EXPIRED)
        val root = RootViewModel(deps)
        root.handleAuthCallback("agentpro://auth-callback?type=recovery#error_code=otp_expired")
        testScheduler.advanceUntilIdle()
        assertEquals(RecoveryStep.Expired, root.recovery.value)
        root.closeRecovery()
        assertNull(root.recovery.value)
        assertEquals(AuthState.SignedOut, deps.auth.state.value)
    }

    @Test
    fun confirmationLinkDoesNotOpenTheFlow() = runTest {
        val deps = FakeAppDependencies()
        val root = RootViewModel(deps)
        root.handleAuthCallback("agentpro://auth-callback#access_token=at&refresh_token=rt&type=signup")
        testScheduler.advanceUntilIdle()
        assertNull(root.recovery.value)
        assertIs<AuthState.SignedIn>(deps.auth.state.value)
    }
}
