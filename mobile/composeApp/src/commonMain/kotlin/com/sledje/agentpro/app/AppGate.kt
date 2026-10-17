package com.sledje.agentpro.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.auth.data.AuthCallbackLink
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.auth.ui.RecoveryStep
import com.sledje.agentpro.feature.settings.data.Profile
import com.sledje.agentpro.feature.website.data.Site
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app starts (docs/UX.md, Navigation map: Splash → Welcome / Onboarding / Main). */
sealed interface AppGate {
    /** Splash: reading the session, or waiting for the first sync of an empty cache. */
    data object Loading : AppGate

    /** SUPABASE_URL / SUPABASE_ANON_KEY are missing from this build. */
    data object NotConfigured : AppGate

    data object SignedOut : AppGate

    /**
     * Session, but profile or site missing. Onboarding always opens on step 1: sign-up already filled the
     * name and mobile, so step 1 shows them with Edit and asks only for the rest (docs/UX.md, Onboarding).
     */
    data object Onboarding : AppGate

    data object Ready : AppGate

    /** The cache is empty and the first sync failed (e.g. offline on a fresh install): Retry. */
    data object Error : AppGate
}

/**
 * Decides the gate (pure, unit-tested). A signed-in agent is Ready once the profile is complete and a
 * site exists. While onboarding runs ([onboardingActive]) the gate stays on onboarding, so creating the
 * site doesn't jump to Main before "Publish now" finishes.
 */
fun resolveGate(
    configured: Boolean,
    auth: AuthState,
    profile: Profile?,
    site: Site?,
    sync: SyncStatus,
    onboardingActive: Boolean,
): AppGate {
    if (!configured) return AppGate.NotConfigured
    return when (auth) {
        AuthState.Loading -> AppGate.Loading
        AuthState.SignedOut -> AppGate.SignedOut
        is AuthState.SignedIn -> when {
            onboardingActive -> AppGate.Onboarding
            profile == null -> if (sync == SyncStatus.Failed) AppGate.Error else AppGate.Loading
            profile.isComplete && site != null -> AppGate.Ready
            // The site may simply not have synced yet: wait for the first refresh to finish.
            site == null && profile.isComplete && (sync == SyncStatus.Idle || sync == SyncStatus.Running) -> AppGate.Loading
            else -> AppGate.Onboarding
        }
    }
}

/**
 * The root ViewModel: resolves the [AppGate], starts the initial sync once a session exists, and
 * latches onboarding (see [OnboardingSession]).
 */
class RootViewModel(private val deps: AppDependencies) : ViewModel() {
    val gate: StateFlow<AppGate> = combine(
        deps.auth.state,
        deps.profiles.observeMine(),
        deps.sites.observeMine(),
        deps.dataSync.status,
        deps.onboarding.active,
    ) { auth, profile, site, sync, onboarding ->
        resolveGate(deps.isConfigured, auth, profile, site, sync, onboarding)
    }
        .distinctUntilChanged()
        .onEach { if (it is AppGate.Onboarding) deps.onboarding.start() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, if (deps.isConfigured) AppGate.Loading else AppGate.NotConfigured)

    init {
        viewModelScope.launch {
            var signedInAs: String? = null
            deps.auth.state.collect { auth ->
                when (auth) {
                    is AuthState.SignedIn -> if (auth.userId != signedInAs) {
                        signedInAs = auth.userId
                        deps.dataSync.onSignedIn(auth.userId)
                        deps.dataSync.refreshAll()
                    }
                    AuthState.SignedOut -> {
                        if (signedInAs != null) deps.onboarding.finish()
                        signedInAs = null
                    }
                    AuthState.Loading -> Unit
                }
            }
        }
    }

    /** Retry from the startup error, and the foreground refresh (ON_START). */
    fun refresh() {
        if (deps.auth.state.value is AuthState.SignedIn && deps.dataSync.status.value != SyncStatus.Running) viewModelScope.launch { deps.dataSync.refreshAll() }
    }

    private val _recovery = MutableStateFlow<RecoveryStep?>(null)

    /** Non-null while a password-reset link's flow is open (docs/UX.md, Set new password); shown above the gate. */
    val recovery: StateFlow<RecoveryStep?> = _recovery.asStateFlow()

    /**
     * Imports the session from an auth link. A reset link (`type=recovery`) opens Set new password once
     * its session is in, or "This link has expired" when the link is expired or invalid. Other links
     * (email confirmation) just sign in; the gate moves on.
     */
    fun handleAuthCallback(url: String) {
        val recoveryLink = AuthCallbackLink.parse(url).recovery
        if (recoveryLink) _recovery.value = RecoveryStep.Verifying
        viewModelScope.launch {
            val result = deps.auth.handleAuthCallback(url)
            if (recoveryLink) _recovery.value = if (result is Result.Ok) RecoveryStep.SetPassword else RecoveryStep.Expired
        }
    }

    /** Leaves the reset flow (Back, or Back to sign in). */
    fun closeRecovery() {
        _recovery.value = null
    }

    /** Saved: Home with "Password updated" (the main shell starts on Home). */
    fun onPasswordSaved() {
        deps.events.post(AppMessage.PasswordUpdated)
        _recovery.value = null
    }
}
