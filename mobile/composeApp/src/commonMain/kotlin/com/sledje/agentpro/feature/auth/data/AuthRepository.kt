package com.sledje.agentpro.feature.auth.data

import com.sledje.agentpro.core.catalog.NoticeConsent
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.remoteCall
import com.sledje.agentpro.feature.privacy.data.encodePendingConsent
import com.sledje.agentpro.feature.privacy.data.recordPendingSignupConsent
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromFragment
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Who is signed in. [Loading] until the stored session has been read. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?) : AuthState
}

/** What Create account asks for. Name and mobile go to the profile, so onboarding doesn't ask for them again. */
data class NewAccount(val fullName: String, val phone: String, val email: String, val password: String)

/** What sign-up led to. */
enum class SignUpOutcome {
    /** Email confirmation is off in the Auth project: the session has started, so the gate moves on. */
    SignedIn,

    /** "Check your email" follows: the agent enters the code from the email ([AuthRepository.verifySignUpCode]). */
    ConfirmEmail,

    /** The address already has an account (Supabase hides that: a fake user, no email). Never reaches the screen: it becomes `Conflict`. */
    AccountExists,
}

/**
 * Google sign-in (DECISIONS #112), and email + password auth with a one-time code from the email in place of links:
 * sign-up is confirmed and a forgotten password is reset by entering the code (DECISIONS #3, #84; docs/BACKEND.md, Auth).
 */
interface AuthRepository {
    val state: StateFlow<AuthState>

    suspend fun signIn(email: String, password: String): AppResult<Unit>

    /**
     * Creates the account. The name and mobile go in the user metadata (`data.full_name`, `data.phone`),
     * and the signup trigger copies them into `profiles`. [consent] (the sign-up notice the person ticked,
     * in the language shown) goes in as `data.consent = {notice_key, version, hash}`; the backend records
     * it (docs/PRIVACY.md, section 1).
     */
    suspend fun signUp(account: NewAccount, consent: NoticeConsent): AppResult<SignUpOutcome>

    /**
     * "Continue with Google" (DECISIONS #112): signs in, or creates the account, with the Google ID token from
     * the account chooser (`signInWith(IDToken)`, [rawNonce] the nonce whose hash the token carries). The signup
     * trigger copies Google's name and email into `profiles`. A Google sign-in carries no sign-up metadata, so
     * [consent] (the sign-up notice ticked on Welcome) is kept as pending and recorded with `record_consent` right
     * after; if that call fails, onboarding records it before asking anything ([ConsentRepository.recordPendingSignupConsent]).
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String, consent: NoticeConsent): AppResult<Unit>

    /**
     * Confirms the email with the code from the sign-up email; success starts the session, so the gate
     * moves on. A wrong or expired code is `Validation("code")`.
     */
    suspend fun verifySignUpCode(email: String, code: String): AppResult<Unit>

    /** Sends the confirmation code again. */
    suspend fun resendConfirmation(email: String): AppResult<Unit>

    /** Sends the reset code. */
    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    /**
     * Checks the code from the reset email; success starts a recovery session, after which
     * [updatePassword] sets the new password. A wrong or expired code is `Validation("code")`.
     */
    suspend fun verifyRecoveryCode(email: String, code: String): AppResult<Unit>

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

/** The sign-up user metadata: `{full_name, phone, consent: {notice_key, version, hash}}` (pure, unit-tested). */
fun signUpMetadata(account: NewAccount, consent: NoticeConsent): JsonObject = buildJsonObject {
    put("full_name", account.fullName.trim())
    put("phone", account.phone)
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

    /** Supabase sends at most one auth email per address per this many seconds (Auth → Rate Limits). */
    const val EMAIL_RESEND_SECONDS = 60

    /** Supabase's email code is 6 digits by default and can be set up to 10 (Auth → Emails → OTP length). */
    const val CODE_MIN_LENGTH = 6
    const val CODE_MAX_LENGTH = 10
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

    override suspend fun signUp(account: NewAccount, consent: NoticeConsent): AppResult<SignUpOutcome> = remoteCall {
        val auth = supabase.require().auth
        val email = account.email.trim()
        val user = auth.signUpWith(Email) {
            this.email = email
            this.password = account.password
            this.data = signUpMetadata(account, consent)
        }
        // With email confirmation on, Supabase hides that the address is taken: it answers 200 with an
        // obfuscated user whose `identities` is empty, and sends no email. A new user always has one.
        if (user != null && user.identities?.isEmpty() == true) {
            SignUpOutcome.AccountExists
        } else if (auth.currentSessionOrNull() != null) {
            SignUpOutcome.SignedIn
        } else {
            SignUpOutcome.ConfirmEmail
        }
    }.let { result ->
        if (result is Result.Ok && result.value == SignUpOutcome.AccountExists) Result.Err(AppError.Conflict) else result
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String, consent: NoticeConsent): AppResult<Unit> {
        // Kept first: the session starts (and the gate leaves Welcome) before record_consent returns.
        store.put(StoreKeys.PENDING_SIGNUP_CONSENT, encodePendingConsent(consent))
        val result = remoteCall {
            supabase.require().auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
            }
        }
        if (result is Result.Err) {
            store.put(StoreKeys.PENDING_SIGNUP_CONSENT, null)
            return result
        }
        // Not tied to the screen that asked: Welcome goes away as soon as the session starts.
        withContext(NonCancellable) { recordPendingSignupConsent(supabase, store) }
        return Result.Ok(Unit)
    }

    override suspend fun verifySignUpCode(email: String, code: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.verifyEmailOtp(OtpType.Email.SIGNUP, email.trim(), code.trim())
    }

    override suspend fun resendConfirmation(email: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.resetPasswordForEmail(email.trim())
    }

    override suspend fun verifyRecoveryCode(email: String, code: String): AppResult<Unit> = remoteCall {
        supabase.require().auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), code.trim())
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
