package com.sledje.agentpro.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.designsystem.currentAppLocale
import com.sledje.agentpro.feature.privacy.data.PrivacyLinks
import com.sledje.agentpro.feature.privacy.ui.NoticeScreen
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormScreen
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.auth.ui.CheckEmailScreen
import com.sledje.agentpro.feature.auth.ui.LinkExpiredScreen
import com.sledje.agentpro.feature.auth.ui.RecoveryStep
import com.sledje.agentpro.feature.auth.ui.ResendEmailViewModel
import com.sledje.agentpro.feature.auth.ui.ResetSentScreen
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordScreen
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordViewModel
import com.sledje.agentpro.feature.auth.ui.WelcomeScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Signed-out routes (docs/UX.md: Welcome → Sign in ⇄ Create account → Check your email; Forgot password → Reset sent). */
object AuthRoutes {
    @Serializable data object Welcome
    @Serializable data object SignIn
    @Serializable data object SignUp

    /** The in-app privacy notice, opened from the Create account consent line. */
    @Serializable data object Notice
    /** [sendNow]: opened from Sign in for an unconfirmed email, so the confirmation is sent on open. */
    @Serializable data class CheckEmail(val email: String, val sendNow: Boolean = false)
    @Serializable data object ForgotPassword
    @Serializable data class ResetSent(val email: String)

    /** Opened only by a reset link (docs/UX.md, Set new password). */
    @Serializable data object SetNewPassword
    @Serializable data object LinkExpired
}

/** The signed-out navigation graph, shown by the app gate while there is no session. */
@Composable
fun AuthNavHost(deps: AppDependencies) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    NavHost(nav, startDestination = AuthRoutes.Welcome) {
        composable<AuthRoutes.Welcome> {
            WelcomeScreen(
                currentLocale = currentAppLocale(),
                onSwitchLocale = { scope.launch { deps.locale.set(it) } },
                onCreateAccount = { nav.navigate(AuthRoutes.SignUp) },
                onSignIn = { nav.navigate(AuthRoutes.SignIn) },
            )
        }
        composable<AuthRoutes.SignIn> {
            AuthForm(deps, AuthFormMode.SignIn, onBack = { nav.popBackStack() },
                onSwitchMode = { nav.navigate(AuthRoutes.SignUp) { popUpTo(AuthRoutes.Welcome) } },
                onForgotPassword = { nav.navigate(AuthRoutes.ForgotPassword) },
                onSent = {},
                onResendConfirmation = { email -> nav.navigate(AuthRoutes.CheckEmail(email, sendNow = true)) })
        }
        composable<AuthRoutes.SignUp> {
            AuthForm(deps, AuthFormMode.SignUp, onBack = { nav.popBackStack() },
                onSwitchMode = { nav.navigate(AuthRoutes.SignIn) { popUpTo(AuthRoutes.Welcome) } },
                onForgotPassword = {},
                onSent = { email -> nav.navigate(AuthRoutes.CheckEmail(email)) { popUpTo(AuthRoutes.Welcome) } },
                onOpenNotice = { nav.navigate(AuthRoutes.Notice) })
        }
        composable<AuthRoutes.Notice> { NoticeScreen(PrivacyNotice.AgentSignup, onBack = { nav.popBackStack() }) }
        composable<AuthRoutes.ForgotPassword> {
            AuthForm(deps, AuthFormMode.ResetPassword, onBack = { nav.popBackStack() },
                onSwitchMode = {}, onForgotPassword = {},
                onSent = { email -> nav.navigate(AuthRoutes.ResetSent(email)) { popUpTo(AuthRoutes.SignIn) } })
        }
        composable<AuthRoutes.CheckEmail> { entry ->
            val route = entry.toRoute<AuthRoutes.CheckEmail>()
            val vm = viewModel { ResendEmailViewModel(route.email, deps.auth::resendConfirmation, sendOnOpen = route.sendNow) }
            val state by vm.state.collectAsState()
            // Back from the email app or browser after confirming: sign in with the details just typed,
            // so the gate moves on to onboarding. The first resume is this screen opening, so skip it.
            var opened by remember { mutableStateOf(false) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (opened) scope.launch { deps.auth.signInAfterConfirmation() } else opened = true
            }
            CheckEmailScreen(
                email = route.email,
                state = state,
                onOpenEmailApp = deps.urlLauncher::openEmailApp,
                onResend = vm::resend,
                onResentShown = vm::onResentShown,
                onErrorShown = vm::onErrorShown,
                onBack = { nav.popBackStack() },
            )
        }
        composable<AuthRoutes.ResetSent> { entry ->
            ResetSentRoute(deps, entry.toRoute<AuthRoutes.ResetSent>().email, onBackToSignIn = { nav.popBackStack() })
        }
    }
}

/**
 * Where a password-reset link leads, shown by the app gate above everything else
 * (docs/UX.md, Set new password): Set new password, or "This link has expired" → Send a new link →
 * Forgot password → Reset sent. [onClose] leaves the flow (the agent stays signed in if the link
 * signed them in); [onPasswordSaved] goes to Home with "Password updated".
 */
@Composable
fun PasswordRecoveryNavHost(
    deps: AppDependencies,
    step: RecoveryStep,
    onClose: () -> Unit,
    onPasswordSaved: () -> Unit,
) {
    val nav = rememberNavController()
    val start: Any = if (step == RecoveryStep.Expired) AuthRoutes.LinkExpired else AuthRoutes.SetNewPassword
    NavHost(nav, startDestination = start) {
        composable<AuthRoutes.SetNewPassword> {
            val vm = viewModel { SetNewPasswordViewModel(deps.auth) }
            val state by vm.state.collectAsState()
            LaunchedEffect(state.saved, state.expired) {
                when {
                    state.saved -> onPasswordSaved()
                    state.expired -> nav.navigate(AuthRoutes.LinkExpired) { popUpTo(AuthRoutes.SetNewPassword) { inclusive = true } }
                }
            }
            SetNewPasswordScreen(vm.form, state, onSave = vm::submit, onBack = onClose)
        }
        composable<AuthRoutes.LinkExpired> {
            LinkExpiredScreen(onSendNewLink = { nav.navigate(AuthRoutes.ForgotPassword) }, onBack = onClose)
        }
        composable<AuthRoutes.ForgotPassword> {
            AuthForm(deps, AuthFormMode.ResetPassword, onBack = { nav.popBackStack() },
                onSwitchMode = {}, onForgotPassword = {},
                onSent = { email -> nav.navigate(AuthRoutes.ResetSent(email)) { popUpTo(AuthRoutes.LinkExpired) } })
        }
        composable<AuthRoutes.ResetSent> { entry ->
            ResetSentRoute(deps, entry.toRoute<AuthRoutes.ResetSent>().email, onBackToSignIn = onClose)
        }
    }
}

/** "Reset link sent" with Resend (the reset email again, after the countdown). */
@Composable
private fun ResetSentRoute(deps: AppDependencies, email: String, onBackToSignIn: () -> Unit) {
    val vm = viewModel { ResendEmailViewModel(email, deps.auth::sendPasswordReset) }
    val state by vm.state.collectAsState()
    ResetSentScreen(
        email = email,
        state = state,
        onResend = vm::resend,
        onResentShown = vm::onResentShown,
        onErrorShown = vm::onErrorShown,
        onBackToSignIn = onBackToSignIn,
    )
}

@Composable
private fun AuthForm(
    deps: AppDependencies,
    mode: AuthFormMode,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onSent: (String) -> Unit,
    onOpenNotice: () -> Unit = {},
    onResendConfirmation: (String) -> Unit = {},
) {
    val vm = viewModel(key = mode.name) { AuthFormViewModel(mode, deps.auth) }
    val state by vm.state.collectAsState()
    val locale = currentAppLocale()
    LaunchedEffect(state.sentTo) {
        state.sentTo?.let { onSent(it); vm.onNavigated() }
    }
    AuthFormScreen(
        mode, vm.form, state,
        onSubmit = { vm.submit(locale) },
        onBack = onBack,
        onSwitchMode = onSwitchMode,
        onForgotPassword = onForgotPassword,
        onLink = { key ->
            when (key) {
                AuthFormViewModel.LINK_NOTICE -> onOpenNotice()
                // Terms are the web page; without a configured host the notice is all there is to show.
                AuthFormViewModel.LINK_TERMS -> PrivacyLinks.terms()?.let { deps.urlLauncher.openUrl(it) } ?: onOpenNotice()
            }
        },
        onResendConfirmation = { (vm.form.values()[AuthFormViewModel.EMAIL] as? String)?.trim()?.let(onResendConfirmation) },
    )
}
