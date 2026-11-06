package app.locker.vault

import app.locker.vault.core.Totp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TotpTest {
    @Test
    fun rfc6238Sha1Vector() {
        val config = Totp.parse("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        assertNotNull(config)
        assertEquals("94287082", Totp.code(config!!.copy(digits = 8), 59_000L))
    }

    @Test
    fun parsesOtpauthUri() {
        val config = Totp.parse(
            "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&digits=6&period=30"
        )
        assertNotNull(config)
        assertEquals("Example", config!!.issuer)
        assertEquals("alice@example.com", config.account)
        assertEquals(6, config.digits)
        assertEquals(30, config.period)
    }
}
