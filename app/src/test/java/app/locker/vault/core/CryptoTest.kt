package app.locker.vault.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    @Test
    fun aesGcmRoundTripAndTamperDetection() {
        val key = Crypto.randomBytes(32)
        val aad = "test:aad".toByteArray()
        val plaintext = "the quick brown fox jumps over the lazy dog".toByteArray()

        val encrypted = Crypto.encrypt(key, plaintext, aad)
        assertArrayEquals(plaintext, Crypto.decrypt(key, encrypted, aad))

        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0x01).toByte()
        assertNull(Crypto.decrypt(key, tampered, aad))

        val wrongAad = Crypto.encrypt(key, plaintext, "other".toByteArray())
        assertNull(Crypto.decrypt(key, wrongAad, aad))
    }

    @Test
    fun argon2IsDeterministicAndPasswordSpecific() {
        val salt = Crypto.randomBytes(16)
        val first = Crypto.deriveKey("correct horse battery staple".toCharArray(), salt)
        val second = Crypto.deriveKey("correct horse battery staple".toCharArray(), salt)
        val wrong = Crypto.deriveKey("wrong password".toCharArray(), salt)

        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(wrong))
        assertTrue(first.size == 32)
    }

    @Test
    fun recoveryCodeRoundTrips() {
        val (secret, display) = Base32.createRecoverySecret()
        val parsed = Base32.parseRecoverySecret(display)
        assertArrayEquals(secret, parsed)
        assertFalse(secret.contentEquals(Crypto.randomBytes(32)))
        assertNull(Base32.parseRecoverySecret("nonsense"))
        assertNull(Base32.parseRecoverySecret(display.dropLast(2)))
    }

    @Test
    fun sha256IsStable() {
        val digest = Crypto.sha256Hex("locker".toByteArray())
        assertTrue(digest.startsWith("8bca6dad"))
    }
}
