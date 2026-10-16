package com.sledje.agentpro.core.data

import com.sledje.agentpro.core.platform.SecureSettings
import com.sledje.agentpro.core.platform.UnreadableSecretException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The supabase-kt session over [SecureSettings] (DECISIONS #44). No backend: an in-memory fake store. */
class SecureSessionManagerTest {
    private class MemorySecureSettings : SecureSettings {
        val values = mutableMapOf<String, String>()
        /** Simulates a lost Keystore key / corrupt ciphertext: the next read deletes and throws. */
        var unreadable = false

        override suspend fun get(key: String): String? {
            if (unreadable && key in values) {
                values.remove(key)
                throw UnreadableSecretException(key)
            }
            return values[key]
        }

        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    private val session = UserSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = null,
    )

    @Test
    fun sessionRoundTrips() = runTest {
        val store = MemorySecureSettings()
        val manager = SecureSessionManager(store)
        manager.saveSession(session)
        assertTrue(store.values.getValue(SecureSessionManager.KEY).contains("refresh-token"))
        val loaded = manager.loadSessionOrNull()
        assertEquals(session.accessToken, loaded?.accessToken)
        assertEquals(session.refreshToken, loaded?.refreshToken)
        assertEquals(session.expiresAt, loaded?.expiresAt)
    }

    @Test
    fun nothingStoredLoadsNull() = runTest {
        var reported = 0
        assertFailsWith<NoSessionFoundException> { SecureSessionManager(MemorySecureSettings()) { reported++ }.loadSession() }
        assertEquals(0, reported, "an empty store is a normal sign-out, not a failure")
    }

    @Test
    fun deleteRemovesTheSession() = runTest {
        val store = MemorySecureSettings()
        val manager = SecureSessionManager(store)
        manager.saveSession(session)
        manager.deleteSession()
        assertTrue(store.values.isEmpty())
        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun unreadableSecretIsClearedAndReported() = runTest {
        val store = MemorySecureSettings()
        var reported = 0
        val manager = SecureSessionManager(store) { reported++ }
        manager.saveSession(session)
        store.unreadable = true
        assertFailsWith<NoSessionFoundException>("no crash: signed out") { manager.loadSession() }
        assertEquals(1, reported)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun corruptJsonIsClearedAndReported() = runTest {
        val store = MemorySecureSettings()
        store.values[SecureSessionManager.KEY] = "{not json"
        var reported = 0
        assertFailsWith<NoSessionFoundException> { SecureSessionManager(store) { reported++ }.loadSession() }
        assertEquals(1, reported)
        assertTrue(store.values.isEmpty())
    }
}
