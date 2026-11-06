package app.locker.vault.core

import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 TOTP. Fully offline; secrets are stored only inside the encrypted item payload.
 */
object Totp {
    data class Config(
        val secret: ByteArray,
        val digits: Int = 6,
        val period: Int = 30,
        val algorithm: String = "SHA1",
        val issuer: String = "",
        val account: String = ""
    ) {
        fun wipe() = secret.fill(0)
    }

    fun parse(input: String): Config? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("otpauth://", ignoreCase = true)) {
            val secret = decodeBase32(value) ?: return null
            if (secret.isEmpty()) return null
            return Config(secret)
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.host.equals("totp", ignoreCase = true)) return null
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "")
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter('=', "")
            key to runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }.toMap()
        val secretText = query["secret"] ?: return null
        val secret = decodeBase32(secretText) ?: return null
        if (secret.isEmpty()) return null
        val digits = query["digits"]?.toIntOrNull()?.coerceIn(4, 10) ?: 6
        val period = query["period"]?.toIntOrNull()?.coerceIn(10, 120) ?: 30
        val algorithm = when (query["algorithm"]?.uppercase()) {
            "SHA256" -> "SHA256"
            "SHA512" -> "SHA512"
            else -> "SHA1"
        }
        val label = uri.path?.removePrefix("/").orEmpty()
        val issuer = query["issuer"]
            ?: label.substringBefore(':', "").takeIf { it.isNotBlank() }
            ?: ""
        val account = label.substringAfter(':', label)
        return Config(secret, digits, period, algorithm, issuer, account)
    }

    fun code(config: Config, timeMillis: Long = System.currentTimeMillis()): String {
        val counter = timeMillis / 1000L / config.period.coerceAtLeast(1)
        val mac = Mac.getInstance("Hmac${config.algorithm}")
        mac.init(SecretKeySpec(config.secret, "RAW"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val modulus = 10.0.pow(config.digits.toDouble()).toInt()
        return (binary % modulus).toString().padStart(config.digits, '0')
    }

    fun secondsRemaining(config: Config, timeMillis: Long = System.currentTimeMillis()): Int {
        val period = config.period.coerceAtLeast(1)
        val elapsed = (timeMillis / 1000L) % period
        return (period - elapsed).toInt().coerceIn(0, period)
    }

    private fun decodeBase32(input: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = buildString {
            input.uppercase().forEach { ch ->
                when (ch) {
                    ' ', '-', '_' -> Unit
                    '=' -> Unit
                    in alphabet -> append(ch)
                    else -> return null
                }
            }
        }
        if (cleaned.isEmpty()) return null
        val output = ByteArray(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        cleaned.forEach { ch ->
            val value = alphabet.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                output[index++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        return output.copyOf(index)
    }
}
