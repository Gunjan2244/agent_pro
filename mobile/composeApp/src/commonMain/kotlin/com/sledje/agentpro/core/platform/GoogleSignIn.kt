package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable

/** What the Google account chooser returned. */
sealed interface GoogleSignInResult {
    /**
     * A Google ID token for Supabase (`signInWith(IDToken)`). The token carries the SHA-256 of [rawNonce];
     * Supabase hashes [rawNonce] again and compares, so a replayed token is refused.
     */
    data class Token(val idToken: String, val rawNonce: String) : GoogleSignInResult

    /** The agent closed the chooser. Nothing is shown. */
    data object Cancelled : GoogleSignInResult

    /** No Google account on the phone (or Google Play services missing): email sign-up is the way in. */
    data object NoAccount : GoogleSignInResult

    /** Anything else (offline, misconfigured client ID); [reason] is for logs only, never shown. */
    data class Failed(val reason: String?) : GoogleSignInResult
}

/**
 * "Continue with Google" (docs/ARCHITECTURE.md, Platform seams; DECISIONS #112). Android: Credential Manager's
 * Sign in with Google sheet with the web client ID (`GOOGLE_WEB_CLIENT_ID`) as the server client ID and a fresh
 * nonce per try; iOS: Google Sign-In for iOS (iOS phase).
 *
 * Composable because the chooser needs the current Activity. Call it once per screen; the returned function
 * opens the chooser.
 */
interface GoogleSignIn {
    /** False when this build has no `GOOGLE_WEB_CLIENT_ID`: the app hides the Google button. */
    val isAvailable: Boolean

    /** [onResult] gets the outcome of one try. */
    @Composable
    fun rememberGoogleSignIn(onResult: (GoogleSignInResult) -> Unit): () -> Unit
}

/** Google sign-in that isn't available (platforms or tests without it, or no client ID configured). */
object NoGoogleSignIn : GoogleSignIn {
    override val isAvailable: Boolean = false

    @Composable
    override fun rememberGoogleSignIn(onResult: (GoogleSignInResult) -> Unit): () -> Unit = { onResult(GoogleSignInResult.NoAccount) }
}
