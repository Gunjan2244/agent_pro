package com.sledje.agentpro.core.platform

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An encrypted value: the GCM [iv] and the [ciphertext] (with its tag). */
class SealedBox(val iv: ByteArray, val ciphertext: ByteArray)

/**
 * Authenticated encryption for [KeystoreSecureSettings]. The real one is [AndroidKeystoreCipher];
 * host tests use a software AES-GCM key, because Robolectric has no AndroidKeyStore provider.
 */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): SealedBox

    /** Throws when the key is gone or the box was tampered with (GCM tag mismatch). */
    fun decrypt(box: SealedBox): ByteArray

    /** Deletes the key, so the next [encrypt] creates a fresh one (after an unreadable value). */
    fun reset()
}

/**
 * AES-256-GCM with a key generated inside the Android Keystore. The key never leaves the Keystore
 * (non-exportable); the Keystore picks a random IV for every encryption.
 */
class AndroidKeystoreCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    private fun key(): SecretKey =
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generate()

    private fun generate(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generateKey()
    }

    override fun encrypt(plaintext: ByteArray): SealedBox {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return SealedBox(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(box: SealedBox): ByteArray {
        val key = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw IllegalStateException("No Keystore key '$alias'")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, box.iv)) }
        return cipher.doFinal(box.ciphertext)
    }

    override fun reset() {
        runCatching { keyStore.deleteEntry(alias) }
    }

    companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "agentpro_secure_settings"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}

/**
 * [SecureSettings] on Android (DECISIONS #44): each value is encrypted with [cipher] and stored as
 * Base64 IV + ciphertext in the app-private SharedPreferences file [FILE_NAME]. App data is excluded
 * from backup and device transfer (AndroidManifest `allowBackup="false"`, `res/xml` rules), and the
 * Keystore key can't be exported, so a copied file can't be decrypted elsewhere.
 */
class KeystoreSecureSettings(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SecureSettings {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
        AndroidKeystoreCipher(),
    )

    private val mutex = Mutex()

    override suspend fun get(key: String): String? = mutex.withLock {
        withContext(io) {
            val iv = prefs.getString(ivKey(key), null)
            val data = prefs.getString(dataKey(key), null)
            if (iv == null && data == null) return@withContext null
            try {
                requireNotNull(iv); requireNotNull(data)
                cipher.decrypt(SealedBox(decoder.decode(iv), decoder.decode(data))).decodeToString(throwOnInvalidSequence = true)
            } catch (e: Exception) {
                // Lost key, tampered or truncated data: delete it (and the key) so the next save starts clean.
                prefs.edit().remove(ivKey(key)).remove(dataKey(key)).commit()
                cipher.reset()
                throw UnreadableSecretException(key, e)
            }
        }
    }

    override suspend fun put(key: String, value: String) = mutex.withLock {
        withContext(io) {
            val box = cipher.encrypt(value.encodeToByteArray())
            prefs.edit()
                .putString(ivKey(key), encoder.encodeToString(box.iv))
                .putString(dataKey(key), encoder.encodeToString(box.ciphertext))
                .commit()
            Unit
        }
    }

    override suspend fun remove(key: String) = mutex.withLock {
        withContext(io) {
            prefs.edit().remove(ivKey(key)).remove(dataKey(key)).commit()
            Unit
        }
    }

    companion object {
        const val FILE_NAME = "agentpro_secure"
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()

        fun ivKey(key: String) = "$key.iv"

        fun dataKey(key: String) = "$key.data"
    }
}
