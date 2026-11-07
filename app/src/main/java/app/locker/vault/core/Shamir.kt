package app.locker.vault.core

import java.security.SecureRandom

/**
 * Shamir's Secret Sharing over GF(256) for the 25-digit recovery code. Three shares are
 * issued with a 2-of-3 threshold; any two reconstruct the code, one alone reveals nothing.
 * This is a best-effort paper backup: the shares themselves are printed/written by the user.
 */
object Shamir {
    data class Share(val index: Int, val bytes: ByteArray)

    private val random = SecureRandom()

    fun split(secret: ByteArray, threshold: Int = 2, shareCount: Int = 3): List<Share> {
        require(secret.isNotEmpty()) { "Secret is empty" }
        require(threshold in 2..shareCount) { "Invalid threshold" }
        require(shareCount <= 255) { "Too many shares" }

        val shares = MutableList(shareCount) { index ->
            Share(index + 1, ByteArray(secret.size))
        }
        val coefficients = ByteArray(threshold - 1)
        secret.forEachIndexed { position, value ->
            random.nextBytes(coefficients)
            shares.forEachIndexed { shareIndex, share ->
                val x = share.index
                var result = value.toInt() and 0xFF
                var power = 1
                coefficients.forEach { coefficient ->
                    power = multiply(power, x)
                    result = result xor multiply(coefficient.toInt() and 0xFF, power)
                }
                share.bytes[position] = result.toByte()
            }
        }
        return shares
    }

    fun combine(shares: List<Share>): ByteArray? {
        if (shares.size < 2) return null
        if (shares.map { it.index }.distinct().size != shares.size) return null
        val length = shares.first().bytes.size
        if (shares.any { it.bytes.size != length }) return null
        val secret = ByteArray(length)
        for (position in 0 until length) {
            var value = 0
            shares.forEachIndexed { i, share ->
                val xi = share.index
                var basis = 1
                shares.forEachIndexed { j, other ->
                    if (j != i) {
                        val xj = other.index
                        basis = multiply(basis, divide(xj, xi xor xj))
                    }
                }
                value = value xor multiply(share.bytes[position].toInt() and 0xFF, basis)
            }
            secret[position] = value.toByte()
        }
        return secret
    }

    private fun multiply(a: Int, b: Int): Int {
        var x = a and 0xFF
        var y = b and 0xFF
        var result = 0
        while (y != 0) {
            if (y and 1 != 0) result = result xor x
            val high = x and 0x80
            x = (x shl 1) and 0xFF
            if (high != 0) x = x xor 0x1B
            y = y shr 1
        }
        return result
    }

    private fun divide(a: Int, b: Int): Int = multiply(a, inverse(b))

    private fun inverse(value: Int): Int {
        var result = 1
        var base = value and 0xFF
        var exponent = 254
        while (exponent > 0) {
            if (exponent and 1 != 0) result = multiply(result, base)
            base = multiply(base, base)
            exponent = exponent shr 1
        }
        return result
    }
}
