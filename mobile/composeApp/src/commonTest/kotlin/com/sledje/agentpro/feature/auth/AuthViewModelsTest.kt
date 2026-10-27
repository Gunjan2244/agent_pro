package com.sledje.agentpro.feature.auth

import app.cash.turbine.test
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.feature.auth.data.AuthRules
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.auth.data.SignUpOutcome
import com.sledje.agentpro.feature.auth.ui.AuthFormError
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormUiState
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.auth.ui.ResendEmailViewModel
import com.sledje.agentpro.feature.auth.ui.VerifyCodeViewModel
import com.sledje.agentpro.feature.auth.ui.WelcomeUiState
import com.sledje.agentpro.feature.auth.ui.WelcomeViewModel
import com.sledje.agentpro.core.platform.GoogleSignInResult
import com.sledje.agentpro.feature.auth.ui.toAuthFormError
import com.sledje.agentpro.testing.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    private val auth = FakeAuthRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun AuthFormViewModel.fill(email: String, password: String? = null) {
        form.set(AuthFormViewModel.EMAIL, email)
        password?.let { form.set(AuthFormViewModel.PASSWORD, it) }
    }

    /** Create account with every field valid except the consent box. */
    private fun AuthFormViewModel.fillSignUp(email: String, password: String) {
        form.set(AuthFormViewModel.NAME, " Ramesh Kumar ")
        form.set(AuthFormViewModel.MOBILE, "98765 43210")
        fill(email, password)
        form.set(AuthFormViewModel.CONFIRM_PASSWORD, password)
    }

    @Test
    fun signInSucceedsAndTheSessionStarts() = runTest {
        val vm = AuthFormViewModel(AuthFormMode.SignIn, auth)
        vm.fill(" ramesh@example.com ", "secret")
        vm.state.test {
            assertEquals(AuthFormUiState(), awaitItem())
            vm.submit(AppLocale.En)
            assertTrue(awaitItem().submitting)
            val done = awaitItem()
            assertFalse(done.submitting)
            assertEquals(null, done.error)
            assertEquals(null, done.sentTo, "sign-in needs no navigation; the gate moves on")
        }
        assertEquals(listOf("signIn:ramesh@example.com"), auth.calls)
        assertIs<AuthState.SignedIn>(auth.state.value)
    }

    @Test
    fun wrongCredentialsShowTheHumanError() = runTest {
        auth.signInResult = Result.Err(AppError.Validation("credentials"))
        val vm = AuthFormViewModel(AuthFormMode.SignIn, auth)
        vm.fill("ramesh@example.com", "wrong")
        vm.state.test {
            awaitItem()
            vm.submit(AppLocale.En)
            awaitItem()
            assertEquals(AuthFormError.Credentials, awaitItem().error)
        }
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun invalidFormIsNotSubmitted() = runTest {
        val vm = AuthFormViewModel(AuthFormMode.SignIn, auth)
        vm.fill("not-an-email", "x")
        vm.submit(AppLocale.En)
        testScheduler.advanceUntilIdle()
        assertTrue(auth.calls.isEmpty())
        assertEquals(ValidationError.Email, vm.form.visibleError(AuthFormViewModel.EMAIL))
    }

    @Test
    fun signUpNeedsEightCharactersThenGoesToCheckYourEmail() = runTest {
        val vm = AuthFormViewModel(AuthFormMode.SignUp, auth)
        vm.fillSignUp("new@example.com", "short")
        assertFalse(vm.form.isValid)
        assertEquals(ValidationError.MinLength(AuthRules.PASSWORD_MIN_LENGTH), vm.form.error(AuthFormViewModel.PASSWORD))
        vm.form.set(AuthFormViewModel.PASSWORD, "long enough")
        vm.form.set(AuthFormViewModel.CONFIRM_PASSWORD, "long enough")
        vm.form.set(AuthFormViewModel.CONSENT, true)
        vm.state.test {
            awaitItem()
            vm.submit(AppLocale.En)
            awaitItem()
            assertEquals("new@example.com", awaitItem().sentTo)
            vm.onNavigated()
            assertEquals(null, awaitItem().sentTo)
        }
        assertEquals(listOf("signUp:new@example.com"), auth.calls)
    }

    @Test
    fun signUpAsksForNameMobileAndThePasswordTwice() = runTest {
        assertEquals(
            listOf(
                AuthFormViewModel.NAME, AuthFormViewModel.MOBILE, AuthFormViewModel.EMAIL,
                AuthFormViewModel.PASSWORD, AuthFormViewModel.CONFIRM_PASSWORD, AuthFormViewModel.CONSENT,
            ),
            AuthFormViewModel.fieldsFor(AuthFormMode.SignUp).map { it.key },
        )
        assertEquals(listOf(AuthFormViewModel.EMAIL, AuthFormViewModel.PASSWORD), AuthFormViewModel.fieldsFor(AuthFormMode.SignIn).map { it.key })

        val vm = AuthFormViewModel(AuthFormMode.SignUp, auth)
        vm.fillSignUp("new@example.com", "long enough")
        vm.form.set(AuthFormViewModel.CONSENT, true)
        vm.form.set(AuthFormViewModel.CONFIRM_PASSWORD, "long enougH")
        assertEquals(ValidationError.Mismatch, vm.form.error(AuthFormViewModel.CONFIRM_PASSWORD))
        vm.submit(AppLocale.En)
        runCurrent()
        assertTrue(auth.calls.isEmpty(), "not sent while the passwords differ")

        vm.form.set(AuthFormViewModel.CONFIRM_PASSWORD, "long enough")
        assertNull(vm.form.error(AuthFormViewModel.CONFIRM_PASSWORD))
        vm.submit(AppLocale.En)
        runCurrent()
        val account = auth.signUpAccounts.single()
        assertEquals("Ramesh Kumar", account.fullName)
        assertEquals("+919876543210", account.phone, "the mobile is normalised to +91")
        assertEquals("new@example.com", account.email)
        assertEquals("long enough", account.password)
    }

    @Test
    fun signUpThatStartsASessionNeedsNoCheckYourEmail() = runTest {
        auth.signUpResult = Result.Ok(SignUpOutcome.SignedIn)
        val vm = AuthFormViewModel(AuthFormMode.SignUp, auth)
        vm.fillSignUp("new@example.com", "long enough")
        vm.form.set(AuthFormViewModel.CONSENT, true)
        vm.submit(AppLocale.En)
        runCurrent()
        assertNull(vm.state.value.sentTo, "signed in at once; the gate opens onboarding")
        assertNull(vm.state.value.error)
        assertIs<AuthState.SignedIn>(auth.state.value)
    }

    @Test
    fun signUpWithAnExistingEmail() = runTest {
        auth.signUpResult = Result.Err(AppError.Conflict)
        val vm = AuthFormViewModel(AuthFormMode.SignUp, auth)
        vm.fillSignUp("ramesh@example.com", "long enough")
        vm.form.set(AuthFormViewModel.CONSENT, true)
        vm.state.test {
            awaitItem(); vm.submit(AppLocale.En); awaitItem()
            assertEquals(AuthFormError.AccountExists, awaitItem().error)
        }
    }

    @Test
    fun resetSendsTheLinkWithEmailOnly() = runTest {
        val vm = AuthFormViewModel(AuthFormMode.ResetPassword, auth)
        assertEquals(listOf(AuthFormViewModel.EMAIL), vm.form.fields.map { it.key })
        vm.fill("ramesh@example.com")
        vm.state.test {
            awaitItem(); vm.submit(AppLocale.En); awaitItem()
            assertEquals("ramesh@example.com", awaitItem().sentTo)
        }
        assertEquals(listOf("reset:ramesh@example.com"), auth.calls)
    }

    @Test
    fun offlineErrorIsKeptAsIs() = runTest {
        auth.resetResult = Result.Err(AppError.Offline)
        val vm = AuthFormViewModel(AuthFormMode.ResetPassword, auth)
        vm.fill("ramesh@example.com")
        vm.state.test {
            awaitItem(); vm.submit(AppLocale.En); awaitItem()
            assertEquals(AuthFormError.Other(AppError.Offline), awaitItem().error)
        }
    }

    @Test
    fun errorMapping() {
        assertEquals(AuthFormError.NotConfirmed, AppError.Validation("email", "not_confirmed").toAuthFormError())
        assertEquals(AuthFormError.WeakPassword, AppError.Validation("password", "weak").toAuthFormError())
        assertEquals(AuthFormError.Other(AppError.RateLimited), AppError.RateLimited.toAuthFormError())
        assertEquals(AuthFormError.Other(AppError.EmailLimited(null)), AppError.EmailLimited(null).toAuthFormError())
    }

    @Test
    fun resendWaitsForTheCooldownAfterSignUp() = runTest {
        val vm = ResendEmailViewModel("new@example.com", auth::resendConfirmation)
        runCurrent()
        assertEquals(AuthRules.EMAIL_RESEND_SECONDS, vm.state.value.secondsLeft)
        vm.resend()
        runCurrent()
        assertEquals(emptyList(), auth.calls)

        advanceTimeBy(AuthRules.EMAIL_RESEND_SECONDS * 1_000L + 1)
        assertEquals(0, vm.state.value.secondsLeft)
        vm.resend()
        runCurrent()
        assertEquals(listOf("resend:new@example.com"), auth.calls)
        assertTrue(vm.state.value.resent)
        assertEquals(AuthRules.EMAIL_RESEND_SECONDS, vm.state.value.secondsLeft)
        vm.onResentShown()
        assertFalse(vm.state.value.resent)
    }

    @Test
    fun resendFromSignInSendsOnOpen() = runTest {
        val vm = ResendEmailViewModel("ramesh@example.com", auth::resendConfirmation, sendOnOpen = true)
        runCurrent()
        assertEquals(listOf("resend:ramesh@example.com"), auth.calls)
        assertTrue(vm.state.value.resent)
    }

    @Test
    fun perAddressWaitRestartsTheCountdownWithoutAMessage() = runTest {
        auth.resendResult = Result.Err(AppError.EmailLimited(42))
        val vm = ResendEmailViewModel("ramesh@example.com", auth::resendConfirmation, sendOnOpen = true)
        runCurrent()
        assertEquals(42, vm.state.value.secondsLeft)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun hourlyQuotaShowsItsMessageAndWaits() = runTest {
        auth.resendResult = Result.Err(AppError.EmailLimited(null))
        val vm = ResendEmailViewModel("ramesh@example.com", auth::resendConfirmation, sendOnOpen = true)
        runCurrent()
        assertEquals(AppError.EmailLimited(null), vm.state.value.error)
        assertEquals(AuthRules.EMAIL_RESEND_SECONDS, vm.state.value.secondsLeft)
    }

    @Test
    fun otherFailuresCanBeRetriedAtOnce() = runTest {
        auth.resendResult = Result.Err(AppError.Offline)
        val vm = ResendEmailViewModel("ramesh@example.com", auth::resendConfirmation, sendOnOpen = true)
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertEquals(0, vm.state.value.secondsLeft)
        auth.resendResult = Result.Ok(Unit)
        vm.resend()
        runCurrent()
        assertEquals(2, auth.calls.size)
    }

    @Test
    fun resetSentResendsTheResetLink() = runTest {
        val vm = ResendEmailViewModel("ramesh@example.com", auth::sendPasswordReset)
        advanceTimeBy(AuthRules.EMAIL_RESEND_SECONDS * 1_000L + 1)
        vm.resend()
        runCurrent()
        assertEquals(listOf("reset:ramesh@example.com"), auth.calls)
    }

    @Test
    fun rightSignUpCodeStartsTheSession() = runTest {
        auth.validCode = "012345"
        val vm = VerifyCodeViewModel("new@example.com", auth::verifySignUpCode)
        vm.form.set(VerifyCodeViewModel.CODE, "012345")
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.verified)
        assertEquals(listOf("verifySignUp:new@example.com:012345"), auth.calls)
        assertIs<AuthState.SignedIn>(auth.state.value)
    }

    @Test
    fun wrongCodeShowsItsMessageAndCanBeRetried() = runTest {
        val vm = VerifyCodeViewModel("new@example.com", auth::verifySignUpCode)
        vm.form.set(VerifyCodeViewModel.CODE, "999999")
        vm.submit()
        runCurrent()
        assertEquals(AuthFormError.WrongCode, vm.state.value.error)
        assertFalse(vm.state.value.verified)
        vm.form.set(VerifyCodeViewModel.CODE, "123456")
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.verified)
    }

    @Test
    fun aShortCodeIsNotSubmitted() = runTest {
        val vm = VerifyCodeViewModel("new@example.com", auth::verifySignUpCode)
        vm.form.set(VerifyCodeViewModel.CODE, "123")
        vm.submit()
        runCurrent()
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun resetCodeChecksTheRecoveryCode() = runTest {
        val vm = VerifyCodeViewModel("ramesh@example.com", auth::verifyRecoveryCode)
        vm.form.set(VerifyCodeViewModel.CODE, "123456")
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.verified)
        assertEquals(listOf("verifyRecovery:ramesh@example.com:123456"), auth.calls)
    }

    @Test
    fun codeErrorMapping() {
        assertEquals(AuthFormError.WrongCode, AppError.Validation("code").toAuthFormError())
    }

    // ---- Welcome: Continue with Google (DECISIONS #112) -------------------------------------------------------------

    @Test
    fun googleNeedsTheConsentTickedFirst() = runTest {
        val vm = WelcomeViewModel(auth)
        assertFalse(vm.form.isValid, "the consent box starts unticked")
        assertFalse(vm.startGoogle(), "the chooser doesn't open")
        assertFalse(vm.state.value.signingIn)
        vm.form.set(AuthFormViewModel.CONSENT, true)
        assertTrue(vm.startGoogle())
        assertTrue(vm.state.value.signingIn)
        assertFalse(vm.startGoogle(), "not twice while it runs")
    }

    @Test
    fun aGoogleTokenSignsInWithTheSignUpConsentInTheLanguageShown() = runTest {
        val vm = WelcomeViewModel(auth)
        vm.form.set(AuthFormViewModel.CONSENT, true)
        vm.startGoogle()
        vm.onGoogleResult(GoogleSignInResult.Token("tok", "nonce"), AppLocale.Hi)
        runCurrent()
        assertEquals(listOf("google:tok:nonce"), auth.calls)
        assertEquals(PrivacyNotice.AgentSignup.consent(AppLocale.Hi), auth.googleConsents.single())
        assertIs<AuthState.SignedIn>(auth.state.value, "the gate moves on by itself")
        assertFalse(vm.state.value.signingIn)
        assertNull(vm.state.value.error)
    }

    @Test
    fun googleOutcomesThatDontSignIn() = runTest {
        val vm = WelcomeViewModel(auth)
        vm.form.set(AuthFormViewModel.CONSENT, true)
        vm.startGoogle()
        vm.onGoogleResult(GoogleSignInResult.Cancelled, AppLocale.En)
        assertEquals(WelcomeUiState(), vm.state.value, "closing the chooser shows nothing")
        vm.startGoogle()
        vm.onGoogleResult(GoogleSignInResult.NoAccount, AppLocale.En)
        assertEquals(AuthFormError.GoogleNoAccount, vm.state.value.error)
        vm.startGoogle()
        vm.onGoogleResult(GoogleSignInResult.Failed("bad client id"), AppLocale.En)
        assertEquals(AuthFormError.GoogleFailed, vm.state.value.error)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun aRefusedGoogleTokenShowsOneMessageButOfflineSaysOffline() = runTest {
        val vm = WelcomeViewModel(auth)
        vm.form.set(AuthFormViewModel.CONSENT, true)
        auth.googleResult = Result.Err(AppError.Validation("credentials"))
        vm.startGoogle()
        vm.onGoogleResult(GoogleSignInResult.Token("tok", "nonce"), AppLocale.En)
        runCurrent()
        assertEquals(AuthFormError.GoogleFailed, vm.state.value.error, "never \"Wrong email or password\"")
        auth.googleResult = Result.Err(AppError.Offline)
        vm.startGoogle()
        vm.onGoogleResult(GoogleSignInResult.Token("tok", "nonce"), AppLocale.En)
        runCurrent()
        assertEquals(AuthFormError.Other(AppError.Offline), vm.state.value.error)
        assertIs<AuthState.SignedOut>(auth.state.value)
    }
}
