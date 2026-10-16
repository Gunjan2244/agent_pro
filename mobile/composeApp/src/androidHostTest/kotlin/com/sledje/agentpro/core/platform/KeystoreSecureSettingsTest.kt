package com.sledje.agentpro.core.platform

import android.content.Context
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [KeystoreSecureSettings] on Robolectric (DECISIONS #44). Robolectric has no AndroidKeyStore
 * provider, so the store's logic runs over [SoftwareGcmCipher]: real AES-256-GCM with an in-memory
 * key (same transformation as [AndroidKeystoreCipher]), so tampering really fails the GCM tag.
 * The Keystore cipher itself needs a device test.
 */
@RunWith(RobolectricTestRunner::class)
class KeystoreSecureSettingsTest {
    private class SoftwareGcmCipher : SecretCipher {
        private var key: SecretKey? = null
        var resets = 0

        private fun key() = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }

        override fun encrypt(plaintext: ByteArray): SealedBox {
            val c = Cipher.getInstance(AndroidKeystoreCipher.TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            return SealedBox(c.iv, c.doFinal(plaintext))
        }

        override fun decrypt(box: SealedBox): ByteArray {
            val k = key ?: error("no key")
            val c = Cipher.getInstance(AndroidKeystoreCipher.TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(AndroidKeystoreCipher.TAG_BITS, box.iv)) }
            return c.doFinal(box.ciphertext)
        }

        override fun reset() { key = null; resets++ }
    }

    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences(KeystoreSecureSettings.FILE_NAME, Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val cipher = SoftwareGcmCipher()
    private val settings = KeystoreSecureSettings(prefs, cipher, Dispatchers.Unconfined)

    private val secret = """{"access_token":"a.b.c","refresh_token":"r-123","type":"हिंदी"}"""

    @Test
    fun valueRoundTripsAndIsNotStoredInPlaintext() = runTest {
        settings.put("session", secret)
        assertEquals(secret, settings.get("session"))
        val stored = prefs.all.values.joinToString()
        assertFalse("ciphertext only", stored.contains("r-123"))
        assertTrue(prefs.contains(KeystoreSecureSettings.ivKey("session")))
        assertTrue(prefs.contains(KeystoreSecureSettings.dataKey("session")))
    }

    @Test
    fun missingValueIsNull() = runTest {
        assertNull(settings.get("session"))
    }

    @Test
    fun removeDeletesBothEntries() = runTest {
        settings.put("session", secret)
        settings.remove("session")
        assertNull(settings.get("session"))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun eachSaveUsesAFreshIv() = runTest {
        settings.put("session", secret)
        val first = prefs.getString(KeystoreSecureSettings.ivKey("session"), null)
        settings.put("session", secret)
        assertFalse(first == prefs.getString(KeystoreSecureSettings.ivKey("session"), null))
    }

    @Test
    fun tamperedCiphertextIsClearedAndReported() = runTest {
        settings.put("session", secret)
        val data = prefs.getString(KeystoreSecureSettings.dataKey("session"), null)!!
        val flipped = (if (data[0] == 'A') 'B' else 'A') + data.substring(1)
        prefs.edit().putString(KeystoreSecureSettings.dataKey("session"), flipped).commit()

        assertThrows(UnreadableSecretException::class.java) { kotlinx.coroutines.runBlocking { settings.get("session") } }
        assertTrue("the unreadable entry is deleted", prefs.all.isEmpty())
        assertEquals("the key is reset for the next save", 1, cipher.resets)
        assertNull(settings.get("session"))
        // A new session can be saved and read afterwards.
        settings.put("session", secret)
        assertEquals(secret, settings.get("session"))
    }

    @Test
    fun lostKeyIsClearedAndReported() = runTest {
        settings.put("session", secret)
        cipher.reset() // e.g. the Keystore key was wiped (lock-screen change, restore to a new device)
        assertThrows(UnreadableSecretException::class.java) { kotlinx.coroutines.runBlocking { settings.get("session") } }
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun truncatedEntryIsClearedAndReported() = runTest {
        settings.put("session", secret)
        prefs.edit().remove(KeystoreSecureSettings.ivKey("session")).commit()
        assertThrows(UnreadableSecretException::class.java) { kotlinx.coroutines.runBlocking { settings.get("session") } }
        assertTrue(prefs.all.isEmpty())
    }
}
