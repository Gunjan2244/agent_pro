package com.sledje.agentpro.feature.auth

import app.cash.turbine.test
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.feature.auth.data.AuthRules
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.auth.ui.AuthFormError
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormUiState
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.auth.ui.CheckEmailViewModel
import com.sledje.agentpro.feature.auth.ui.toAuthFormError
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
        vm.fill("new@example.com", "short")
        assertFalse(vm.form.isValid)
        assertEquals(ValidationError.MinLength(AuthRules.PASSWORD_MIN_LENGTH), vm.form.error(AuthFormViewModel.PASSWORD))
        vm.form.set(AuthFormViewModel.PASSWORD, "long enough")
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
    fun signUpWithAnExistingEmail() = runTest {
        auth.signUpResult = Result.Err(AppError.Conflict)
        val vm = AuthFormViewModel(AuthFormMode.SignUp, auth)
        vm.fill("ramesh@example.com", "long enough")
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
    }

    @Test
    fun checkEmailResends() = runTest {
        val vm = CheckEmailViewModel("new@example.com", auth)
        vm.state.test {
            awaitItem()
            vm.resend()
            assertTrue(awaitItem().sending)
            assertTrue(awaitItem().resent)
            vm.onResentShown()
            assertFalse(awaitItem().resent)
        }
        assertEquals(listOf("resend:new@example.com"), auth.calls)
    }
}
