package app.locker.vault

import app.locker.vault.data.PasswordImport
import app.locker.vault.data.TotpImport
import app.locker.vault.core.Totp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportParsingTest {
    @Test
    fun otpauthUriBecomesATotpRow() {
        val rows = TotpImport.parse(
            "otpauth://totp/Example%3Aalice%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        )
        assertEquals(1, rows.size)
        assertEquals("Example:alice@example.com", rows[0].title)
        assertTrue(rows[0].totp.startsWith("otpauth://totp/"))
        assertTrue(rows[0].totp.contains("secret=JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun googleAuthenticatorMigrationKeepsIssuerAndSecret() {
        val secret = "Hello!\uDEAD".toByteArray(Charsets.ISO_8859_1)
        val otp = ByteArrayOutputStream().apply {
            write(fieldBytes(1, secret))
            write(fieldBytes(2, "alice@example.com".toByteArray()))
            write(fieldBytes(3, "Example".toByteArray()))
            write(byteArrayOf(0x30, 0x02)) // type = TOTP
        }.toByteArray()
        val payload = ByteArrayOutputStream().apply {
            write(fieldBytes(1, otp))
            write(byteArrayOf(0x10, 0x01)) // version
        }.toByteArray()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        val rows = TotpImport.parse("otpauth-migration://offline?data=$encoded")

        assertEquals(1, rows.size)
        assertEquals("alice@example.com", rows[0].title)
        assertTrue(rows[0].totp.contains("issuer=Example"))
        assertTrue(rows[0].totp.contains("digits=6"))
        val config = Totp.parse(rows[0].totp)
        assertNotNull(config)
        assertEquals(6, config?.digits)
        assertEquals(6, Totp.code(config!!, 1_700_000_000_000L).length)
    }

    @Test
    fun bitwardenJsonImportsLoginsAndSkipsOtherTypes() {
        val json = """
            {
              "encrypted": false,
              "folders": [{"id": "f1", "name": "Work"}],
              "items": [
                {"type": 1, "name": "Mail", "folderId": "f1", "favorite": true,
                 "notes": "2FA enabled",
                 "login": {"username": "alice", "password": "s3cret", "totp": "JBSWY3DPEHPK3PXP",
                           "uris": [{"uri": "https://mail.example.com"}]}},
                {"type": 2, "name": "A secure note"}
              ]
            }
        """.trimIndent()
        val parsed = PasswordImport.parseBytes(json.toByteArray())

        assertEquals("Bitwarden (JSON)", parsed.provider)
        assertEquals(1, parsed.rows.size)
        assertEquals("Mail", parsed.rows[0].title)
        assertEquals("Work", parsed.rows[0].folder)
        assertTrue(parsed.rows[0].favorite)
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun encryptedBitwardenJsonIsRejected() {
        val json = """{"encrypted": true, "items": []}"""
        val failure = runCatching { PasswordImport.parseBytes(json.toByteArray()) }.exceptionOrNull()
        assertTrue(failure is PasswordImport.Unrecognized)
    }

    @Test
    fun zipWithCsvIsReadAsAnExport() {
        val csv = "name,username,password,url\nMail,alice,s3cret,https://mail.example.com\n"
        val bytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("credentials.csv"))
                zip.write(csv.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val parsed = PasswordImport.parseBytes(bytes)

        assertEquals(1, parsed.rows.size)
        assertEquals("Mail", parsed.rows[0].title)
        assertEquals("s3cret", parsed.rows[0].password)
    }

    private fun fieldBytes(field: Int, value: ByteArray): ByteArray =
        byteArrayOf(((field shl 3) or 2).toByte()) +
            varint(value.size.toLong()) +
            value

    private fun varint(value: Long): ByteArray {
        var remaining = value
        val output = ByteArrayOutputStream()
        while (remaining >= 0x80) {
            output.write(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
        return output.toByteArray()
    }
}
