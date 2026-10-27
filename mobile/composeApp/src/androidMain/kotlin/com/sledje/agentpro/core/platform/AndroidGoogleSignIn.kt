package com.sledje.agentpro.core.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * [GoogleSignIn] on Android through Credential Manager (DECISIONS #112). One tap first: the bottom sheet listing
 * the phone's Google accounts (`GetGoogleIdOption`, every account, no auto-select). When it has nothing to offer,
 * the full "Sign in with Google" dialog (`GetSignInWithGoogleOption`), which can also add an account.
 * [serverClientId] is the OAuth web client ID that Supabase's Google provider accepts as the token audience.
 */
class AndroidGoogleSignIn(private val serverClientId: String) : GoogleSignIn {
    override val isAvailable: Boolean get() = serverClientId.isNotBlank()

    @Composable
    override fun rememberGoogleSignIn(onResult: (GoogleSignInResult) -> Unit): () -> Unit {
        // The Activity: Credential Manager shows its UI over it.
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val callback by rememberUpdatedState(onResult)
        return remember(context, scope) {
            { scope.launch { callback(signIn(context)) } }
        }
    }

    private suspend fun signIn(context: Context): GoogleSignInResult {
        if (!isAvailable) return GoogleSignInResult.NoAccount
        val manager = CredentialManager.create(context)
        val rawNonce = newNonce()
        val hashed = sha256Hex(rawNonce)
        val oneTap = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(hashed)
            .build()
        val first = request(manager, context, oneTap, rawNonce)
        if (first != GoogleSignInResult.NoAccount) return first
        val button = GetSignInWithGoogleOption.Builder(serverClientId).setNonce(hashed).build()
        return request(manager, context, button, rawNonce)
    }

    private suspend fun request(manager: CredentialManager, context: Context, option: CredentialOption, rawNonce: String): GoogleSignInResult =
        try {
            val credential = manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                GoogleSignInResult.Failed("unexpected credential ${credential.type}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.NoAccount
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed(e.type)
        } catch (e: Exception) {
            GoogleSignInResult.Failed(e::class.simpleName)
        }

    private fun newNonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
