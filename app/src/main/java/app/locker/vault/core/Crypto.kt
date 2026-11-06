package app.locker.vault.core

import android.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Small, audited-primitive-only crypto toolbox.
 *
 * - Key derivation: Argon2id (Bouncy Castle)
 * - Authenticated encryption: AES-256-GCM with a fresh random nonce per message
 * - File bodies: chunked AES-256-GCM with a per-file key wrapped by the vault master key
 *
 * The vault master key never leaves the device and is held in memory only while unlocked.
 */
object Crypto {
    const val MASTER_KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128

    data class KdfParams(
        val version: Int,
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int
    )

    private val KDF_V1 = KdfParams(
        version = 1,
        iterations = 3,
        memoryKb = 64 * 1024,
        parallelism = 4
    )
    private val KDF_V2 = KdfParams(
        version = 2,
        iterations = 4,
        memoryKb = 96 * 1024,
        parallelism = 4
    )

    /** New vaults use the newest parameters. Existing ones upgrade after a successful unlock. */
    val currentKdfParams: KdfParams get() = KDF_V2
    val kdfVersion: Int get() = currentKdfParams.version

    fun paramsFor(version: Int): KdfParams? = when (version) {
        1 -> KDF_V1
        2 -> KDF_V2
        else -> null
    }

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** Derives a 256-bit key from a user passphrase. Call off the main thread. */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams = currentKdfParams
    ): ByteArray {
        val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKb)
            .withParallelism(params.parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(argon) }
        val output = ByteArray(MASTER_KEY_BYTES)
        generator.generateBytes(password, output)
        return output
    }

    /** Derives a 256-bit key from an already-random recovery secret. */
    fun deriveKey(
        secret: ByteArray,
        salt: ByteArray,
        params: KdfParams = currentKdfParams
    ): ByteArray {
        val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKb)
            .withParallelism(params.parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(argon) }
        return ByteArray(MASTER_KEY_BYTES).also { generator.generateBytes(secret, it) }
    }

    /** Returns nonce || ciphertext || tag. */
    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext)
        return nonce + encrypted
    }

    /** Returns null when authentication fails (wrong key, tampered data, or wrong AAD). */
    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray? {
        if (blob.size <= NONCE_BYTES) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, NONCE_BYTES))
            )
            cipher.updateAAD(aad)
            cipher.doFinal(blob, NONCE_BYTES, blob.size - NONCE_BYTES)
        } catch (_: Exception) {
            null
        }
    }

    fun encryptStreamChunk(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        length: Int,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext, 0, length)
    }

    fun decryptStreamChunk(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    } catch (_: Exception) {
        null
    }

    fun wipe(vararg arrays: ByteArray?) {
        arrays.forEach { it?.fill(0) }
    }

    fun isEqual(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256Hex(data: ByteArray): String = sha256(data).joinToString("") { "%02x".format(it) }

    fun b64encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun b64decode(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
