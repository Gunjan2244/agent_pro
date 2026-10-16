package com.sledje.agentpro.core.platform

/**
 * Small secrets encrypted at rest (docs/ARCHITECTURE.md, Platform seams; DECISIONS #44). Holds the
 * Supabase login session (`SecureSessionManager`).
 *
 * Android: AES-GCM with a non-exportable key in the Android Keystore; the ciphertext and IV live in
 * app-private SharedPreferences that are excluded from backup (`KeystoreSecureSettings`).
 * iOS: the Keychain (iOS phase).
 */
interface SecureSettings {
    /**
     * The stored value, or null when nothing is stored. When the value can't be decrypted (the key was
     * lost or the ciphertext is corrupt), the entry is deleted and [UnreadableSecretException] is thrown,
     * so the caller can sign out cleanly.
     */
    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String)

    suspend fun remove(key: String)
}

/** A stored secret couldn't be decrypted; it has already been deleted. */
class UnreadableSecretException(key: String, cause: Throwable? = null) :
    IllegalStateException("The stored value for '$key' can't be read and was cleared", cause)
