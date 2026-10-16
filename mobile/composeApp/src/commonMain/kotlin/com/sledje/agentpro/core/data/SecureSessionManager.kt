package com.sledje.agentpro.core.data

import com.sledje.agentpro.core.platform.SecureSettings
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * supabase-kt's session storage, backed by [SecureSettings] so the session is encrypted at rest
 * (DECISIONS #44). Plugged into the Auth plugin as its `sessionManager` (see [SupabaseProvider]).
 *
 * A session that can't be read back (lost Keystore key, corrupt ciphertext or JSON) is deleted and
 * reported through [onUnreadable]; loading then throws [NoSessionFoundException] (supabase-kt's
 * "nothing stored" signal), so it reports "not authenticated" and the app shows Welcome instead of
 * crashing.
 */
class SecureSessionManager(
    private val secure: SecureSettings,
    private val onUnreadable: suspend () -> Unit = {},
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        secure.put(KEY, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        val stored = try {
            secure.get(KEY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // SecureSettings has already deleted the unreadable entry.
            runCatching { secure.remove(KEY) }
            onUnreadable()
            throw NoSessionFoundException()
        } ?: throw NoSessionFoundException()
        return try {
            json.decodeFromString(UserSession.serializer(), stored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            secure.remove(KEY)
            onUnreadable()
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() {
        secure.remove(KEY)
    }

    companion object {
        const val KEY = "supabase_session"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
