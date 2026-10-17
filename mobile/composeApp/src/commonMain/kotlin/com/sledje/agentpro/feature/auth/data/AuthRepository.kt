package com.sledje.agentpro.feature.auth.data

import com.sledje.agentpro.core.catalog.NoticeConsent
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.remoteCall
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromFragment
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Who is signed in. [Loading] until the stored session has been read. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?) : AuthState
}

/** Email + password auth (DECISIONS #3; docs/BACKEND.md, Auth: email confirmation on, minimum 8 characters). */
interface AuthRepository {
    val state: StateFlow<AuthState>

    suspend fun signIn(email: String, password: String): AppResult<Unit>

    /**
     * Creates the account. Email confirmation is on, so no session starts; "Check your email" follows.
     * [consent] (the sign-up notice the person ticked, in the language shown) goes in the user metadata as
     * `data.consent = {notice_key, version, hash}`; the backend records it (docs/PRIVACY.md, section 1).
     */
    suspend fun signUp(email: String, password: String, consent: NoticeConsent): AppResult<Unit>

    suspend fun resendConfirmation(email: String): AppResult<Unit>

    /** Sends the reset link (redirect [AuthCallbackLink.RECOVERY_REDIRECT]). */
    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    /** Sets a new password for the signed-in (recovery) session: Set new password (docs/UX.md). */
    suspend fun updatePassword(password: String): AppResult<Unit>

    suspend fun signOut(): AppResult<Unit>

    /**
     * Imports the session from an `agentpro://auth-callback` link (email confirmation or reset).
     * An expired or invalid link (an Auth error in the link, or no session in it) is
     * `Validation("link", "expired")`.
     */
    suspend fun handleAuthCallback(url: String): AppResult<Unit>
}

/** The sign-up user metadata: `{consent: {notice_key, version, hash}}` (pure, unit-tested). */
fun signUpMetadata(consent: NoticeConsent): JsonObject = buildJsonObject {
    put(
        "consent",
        buildJsonObject {
            put("notice_key", consent.noticeKey)
            put("version", consent.version)
            put("hash", consent.hash)
        },
    )
}

/** Rules of the Supabase Auth project (docs/BACKEND.md, Auth). */
object AuthRules {
    const val PASSWORD_MIN_LENGTH = 8
}

/**
 * [AuthRepository] over supabase-kt Auth. The session is stored encrypted by `SecureSessionManager`
 * (DECISIONS #44). While a stored session can't be refreshed (offline), the last signed-in user
 * still counts as signed in, so the app opens from the cache (docs/UX.md, Splash).
 */
class SupabaseAuthRepository(
    private val supabase: SupabaseProvider,
    private val store: KeyValueStore,
    scope: CoroutineScope,
    private val onSignedOut: suspend () -> Unit,
) : AuthRepository {
    private val notConfigured = MutableStateFlow<AuthState>(AuthState.SignedOut)

    override val state: StateFlow<AuthState> = supabase.client?.let { client ->
        combine(client.auth.sessionStatus, store.observe(StoreKeys.LAST_USER_ID)) { status, lastUser ->
            when (status) {
                SessionStatus.Initializing -> AuthState.Loading
                is SessionStatus.Authenticated -> status.session.user?.let { AuthState.SignedIn(it.id, it.email) }
                    ?: lastUser?.let { AuthState.SignedIn(it, null) } ?: AuthState.SignedOut
                is SessionStatus.RefreshFailure -> lastUser?.let { AuthState.SignedIn(it, null) } ?: AuthState.SignedOut
                is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            }
        }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)
    } ?: notConfigured

    override suspend fun signIn(email: String, password: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String, consent: NoticeConsent): AppResult<Unit> = remoteCall {
        supabase.require().auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.data = signUpMetadata(consent)
        }
        Unit
    }

    override suspend fun resendConfirmation(email: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.resetPasswordForEmail(email.trim(), redirectUrl = AuthCallbackLink.RECOVERY_REDIRECT)
    }

    override suspend fun updatePassword(password: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.updateUser { this.password = password }
        Unit
    }

    override suspend fun signOut(): AppResult<Unit> {
        val result = remoteCall { supabase.require().auth.signOut() }
        // Signing out locally always succeeds; the cache goes either way.
        if (result is Result.Err) runCatching { supabase.client?.auth?.clearSession() }
        onSignedOut()
        return Result.Ok(Unit)
    }

    override suspend fun handleAuthCallback(url: String): AppResult<Unit> {
        val link = AuthCallbackLink.parse(url)
        if (link.error != null || !link.hasSession) return Result.Err(LINK_EXPIRED)
        return remoteCall {
            val auth = supabase.require().auth
            val code = link.code
            val fragment = link.sessionFragment
            when {
                code != null -> { auth.exchangeCodeForSession(code); Unit }
                fragment != null -> auth.importSession(auth.parseSessionFromFragment(fragment))
                else -> Unit
            }
        }
    }

    companion object {
        val LINK_EXPIRED = AppError.Validation("link", "expired")
    }
}
