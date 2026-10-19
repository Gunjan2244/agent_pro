package com.sledje.agentpro.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.LocalAppLocale
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.auth.AuthNavHost
import com.sledje.agentpro.feature.auth.PasswordRecoveryNavHost
import com.sledje.agentpro.feature.auth.ui.RecoveryStep
import com.sledje.agentpro.feature.onboarding.OnboardingNavHost

/**
 * Root composable shared by Android and iOS: applies the in-app language (LocaleController →
 * LocalAppLocale), the theme and the connectivity local, then [AppNavHost].
 */
@Composable
fun App(deps: AppDependencies) {
    val choice by deps.locale.choice.collectAsState()
    val loaded = choice ?: run {
        // The stored language hasn't loaded yet: a blank frame instead of flashing the wrong language.
        AgentProTheme { Surface(Modifier.fillMaxSize()) {} }
        return
    }
    val language = loaded.locale?.key
    CompositionLocalProvider(LocalAppLocale provides language) {
        key(language) {
            val online by deps.network.isOnline.collectAsState()
            CompositionLocalProvider(LocalIsOnline provides online) {
                AgentProTheme {
                    Surface(Modifier.fillMaxSize()) { AppNavHost(deps) }
                }
            }
        }
    }
}

/**
 * The gate (docs/UX.md, Navigation map): Splash → Welcome (signed out) → Onboarding (no profile or
 * site) → Main. Each part has its own NavHost with type-safe `@Serializable` routes, so signing out
 * drops every back stack. Refreshes the cache when the app returns to the foreground.
 */
@Composable
fun AppNavHost(deps: AppDependencies) {
    val root = viewModel { RootViewModel(deps) }
    val gate by root.gate.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { root.refresh() }

    val pending by deps.deepLinks.pending.collectAsState()
    LaunchedEffect(pending) {
        val link = pending
        if (link is DeepLink.AuthCallback) {
            root.handleAuthCallback(link.url)
            deps.deepLinks.consume(link)
        }
    }

    // A password-reset link's flow covers the gate until it's finished or left (docs/UX.md, Set new password).
    val recovery by root.recovery.collectAsState()
    when (val step = recovery) {
        null -> Unit
        RecoveryStep.Verifying -> { SplashScreen(); return }
        else -> {
            key(step) { PasswordRecoveryNavHost(deps, step, onClose = root::closeRecovery, onPasswordSaved = root::onPasswordSaved) }
            return
        }
    }

    // Fade-through between the gate's parts, e.g. Splash → Main or Onboarding → Main (docs/DESIGN_SYSTEM.md, Motion).
    AnimatedContent(gate, Modifier.fillMaxSize(), transitionSpec = { Motion.fadeThrough() }, label = "gate") { shown ->
        when (shown) {
            AppGate.Loading -> SplashScreen()
            AppGate.NotConfigured -> NotConfiguredScreen()
            AppGate.Error -> StartupErrorScreen(onRetry = root::refresh)
            AppGate.SignedOut -> AuthNavHost(deps)
            AppGate.Onboarding -> OnboardingNavHost(deps)
            AppGate.Ready -> MainShell(deps)
        }
    }
}
