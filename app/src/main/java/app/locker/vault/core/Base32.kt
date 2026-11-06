package app.locker.vault.core

/**
 * Crockford Base32 with a one-byte checksum. Used for the recovery key, which wraps the
 * master key independently of the user's password.
 */
object Base32 {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return out.toString()
    }

    fun decode(input: String): ByteArray? {
        val cleaned = buildString {
            input.uppercase().forEach { ch ->
                when (ch) {
                    '0', 'O' -> append('0')
                    '1', 'I', 'L' -> append('1')
                    in ALPHABET -> append(ch)
                    else -> Unit // ignore separators
                }
            }
        }
        if (cleaned.isEmpty()) return null
        val out = ByteArray(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (ch in cleaned) {
            val value = ALPHABET.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out[index++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        return out
    }

    /**
     * The recovery secret is 32 random bytes plus a checksum byte. Rendering it in groups
     * keeps it readable when written down.
     */
    fun formatForDisplay(code: String): String =
        code.chunked(4).joinToString(" ")

    fun createRecoverySecret(): Pair<ByteArray, String> {
        val secret = Crypto.randomBytes(32)
        val checksum = Crypto.sha256(secret).first()
        val full = secret + checksum
        return secret to formatForDisplay(encode(full))
    }

    /** Returns the 32-byte recovery secret, or null if the checksum does not match. */
    fun parseRecoverySecret(display: String): ByteArray? {
        val decoded = decode(display) ?: return null
        if (decoded.size != 33) return null
        val secret = decoded.copyOfRange(0, 32)
        val checksum = decoded[32]
        if (Crypto.sha256(secret).first() != checksum) return null
        return secret
    }
}
