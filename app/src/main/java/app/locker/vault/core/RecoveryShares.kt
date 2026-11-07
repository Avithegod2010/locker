package app.locker.vault.core

/**
 * Paper-friendly encoding for Shamir shares. Each share carries its index and a checksum so
 * a mistyped share is rejected before reconstruction. Shares are not secret on their own,
 * but any two of the three reconstruct the recovery code.
 */
object RecoveryShares {
    private const val PREFIX = "LKR1"
    private const val CHECKSUM_BYTES = 1

    fun split(recoveryCode: String, threshold: Int = 2, shareCount: Int = 3): List<String> {
        val digits = recoveryCode.filter { it.isDigit() }
        require(digits.length == Base32.NUMERIC_SECRET_DIGITS + 1) {
            "The recovery code must contain 25 digits"
        }
        require(Base32.checkDigit(digits.substring(0, Base32.NUMERIC_SECRET_DIGITS)) == digits.last()) {
            "The recovery code check digit is invalid"
        }
        val secret = digits.toByteArray(Charsets.UTF_8)
        return Shamir.split(secret, threshold, shareCount).map(::encode)
    }

    fun combine(shares: List<String>): String? {
        val decoded = shares
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .mapNotNull(::decode)
        if (decoded.size < 2) return null
        val secret = Shamir.combine(decoded) ?: return null
        val text = secret.toString(Charsets.UTF_8)
        if (text.length != Base32.NUMERIC_SECRET_DIGITS + 1) return null
        if (text.any { !it.isDigit() }) return null
        if (Base32.checkDigit(text.substring(0, Base32.NUMERIC_SECRET_DIGITS)) != text.last()) {
            return null
        }
        return Base32.formatForDisplay(text)
    }

    fun encode(share: Shamir.Share): String {
        val payload = share.bytes + Crypto.sha256(share.bytes).copyOf(CHECKSUM_BYTES)
        val encoded = Base32.encode(payload)
        return "$PREFIX-${share.index}-" + encoded.chunked(5).joinToString("-")
    }

    fun decode(value: String): Shamir.Share? {
        val match = Regex("^$PREFIX-(\\d+)-(.+)$", RegexOption.IGNORE_CASE)
            .find(value.trim()) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        if (index !in 1..255) return null
        val encoded = match.groupValues[2].filter { it.isLetterOrDigit() }
        val payload = Base32.decode(encoded) ?: return null
        if (payload.size != Base32.NUMERIC_SECRET_DIGITS + CHECKSUM_BYTES + 1) return null
        val bytes = payload.copyOfRange(0, payload.size - CHECKSUM_BYTES)
        val checksum = payload.last()
        if (Crypto.sha256(bytes).first() != checksum) return null
        return Shamir.Share(index, bytes)
    }
}
