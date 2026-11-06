package app.locker.vault.core

/**
 * Recovery-key helpers. The current format renders the secret as digits only (easy to write
 * down in groups of four); older vaults keep their original Crockford-Base32 key, and both
 * formats are still accepted when unlocking.
 *
 * Numeric format: 24 random digits plus one trailing check digit. The digits are the secret
 * material themselves (about 79.7 bits of entropy stretched with Argon2id before use); the
 * check digit catches most typos before the slow derivation is attempted.
 */
object Base32 {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    const val NUMERIC_SECRET_DIGITS = 24
    private const val NUMERIC_TOTAL_DIGITS = NUMERIC_SECRET_DIGITS + 1 // + check digit

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

    fun formatForDisplay(code: String): String =
        if (code.length == NUMERIC_TOTAL_DIGITS) code.chunked(5).joinToString(" ")
        else code.chunked(4).joinToString(" ")

    /** Same check digit formula as [createRecoverySecret] uses. Pass only the secret digits. */
    fun checkDigit(digits: String): Char {
        val digest = Crypto.sha256(digits.toByteArray(Charsets.UTF_8))
        val value = digest.first().toInt() and 0xFF
        return ('0' + value % 10)
    }

    fun createRecoverySecret(): Pair<ByteArray, String> {
        val digits = randomDigits(NUMERIC_SECRET_DIGITS)
        val code = digits + checkDigit(digits)
        return digits.toByteArray(Charsets.UTF_8) to formatForDisplay(code)
    }

    fun randomDigits(count: Int): String {
        val out = StringBuilder(count)
        val buffer = ByteArray(64)
        var index = 0
        var bufferEnd = 0
        while (out.length < count) {
            if (index >= bufferEnd) {
                bufferEnd = Crypto.randomBytes(buffer.size).copyInto(buffer).size
                index = 0
            }
            // Two bytes give a uniform 0..65535; rejecting >= 65000 avoids modulo bias.
            val raw = (buffer[index].toInt() and 0xFF shl 8) or (buffer[index + 1].toInt() and 0xFF)
            index += 2
            if (raw >= 65000) continue
            out.append('0' + raw % 10)
        }
        return out.toString()
    }

    /**
     * Returns the recovery secret bytes for the entered key, or null when the format or the
     * checksum does not match. Accepts both the numeric format and the legacy Base32 format.
     */
    fun parseRecoverySecret(display: String): ByteArray? {
        // Normalise the look-alike characters users commonly mix up when copying by hand.
        val normalized = display.uppercase().map { ch ->
            when (ch) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> ch
            }
        }.joinToString("")
        val digits = normalized.filter { it.isDigit() }
        if (digits.length == NUMERIC_TOTAL_DIGITS) {
            val secretDigits = digits.substring(0, NUMERIC_SECRET_DIGITS)
            if (checkDigit(secretDigits) != digits.last()) return null
            return secretDigits.toByteArray(Charsets.UTF_8)
        }
        val decoded = decode(display) ?: return null
        if (decoded.size != 33) return null
        val secret = decoded.copyOfRange(0, 32)
        val checksum = decoded[32]
        if (Crypto.sha256(secret).first() != checksum) return null
        return secret
    }
}
