package com.sledje.agentpro.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.designsystem.currentAppLocale
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormScreen
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.auth.ui.CheckEmailScreen
import com.sledje.agentpro.feature.auth.ui.CheckEmailViewModel
import com.sledje.agentpro.feature.auth.ui.LinkExpiredScreen
import com.sledje.agentpro.feature.auth.ui.RecoveryStep
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
    @Serializable data class CheckEmail(val email: String)
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
                onSent = {})
        }
        composable<AuthRoutes.SignUp> {
            AuthForm(deps, AuthFormMode.SignUp, onBack = { nav.popBackStack() },
                onSwitchMode = { nav.navigate(AuthRoutes.SignIn) { popUpTo(AuthRoutes.Welcome) } },
                onForgotPassword = {},
                onSent = { email -> nav.navigate(AuthRoutes.CheckEmail(email)) { popUpTo(AuthRoutes.Welcome) } })
        }
        composable<AuthRoutes.ForgotPassword> {
            AuthForm(deps, AuthFormMode.ResetPassword, onBack = { nav.popBackStack() },
                onSwitchMode = {}, onForgotPassword = {},
                onSent = { email -> nav.navigate(AuthRoutes.ResetSent(email)) { popUpTo(AuthRoutes.SignIn) } })
        }
        composable<AuthRoutes.CheckEmail> { entry ->
            val route = entry.toRoute<AuthRoutes.CheckEmail>()
            val vm = viewModel { CheckEmailViewModel(route.email, deps.auth) }
            val state by vm.state.collectAsState()
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
            val route = entry.toRoute<AuthRoutes.ResetSent>()
            ResetSentScreen(route.email, onBackToSignIn = { nav.popBackStack() })
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
            val route = entry.toRoute<AuthRoutes.ResetSent>()
            ResetSentScreen(route.email, onBackToSignIn = onClose)
        }
    }
}

@Composable
private fun AuthForm(
    deps: AppDependencies,
    mode: AuthFormMode,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onSent: (String) -> Unit,
) {
    val vm = viewModel(key = mode.name) { AuthFormViewModel(mode, deps.auth) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.sentTo) {
        state.sentTo?.let { onSent(it); vm.onNavigated() }
    }
    AuthFormScreen(mode, vm.form, state, vm::submit, onBack, onSwitchMode, onForgotPassword)
}
