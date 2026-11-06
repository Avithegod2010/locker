package app.locker.vault

import app.locker.vault.data.PasswordImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordImportTest {

    @Test
    fun `bitwarden export maps every login field`() {
        val csv = """
            folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
            Work,1,login,GitHub,"hello, world",,,https://github.com,octocat,sup3r!secret,otpauth://totp/GitHub?secret=JBSWY3DPEHPK3PXP
        """.trimIndent()

        val parsed = PasswordImport.parse(csv)

        assertEquals("Bitwarden", parsed.provider)
        assertEquals(1, parsed.rows.size)
        val row = parsed.rows.first()
        assertEquals("GitHub", row.title)
        assertEquals("octocat", row.username)
        assertEquals("sup3r!secret", row.password)
        assertEquals("https://github.com", row.website)
        assertEquals("hello, world", row.notes)
        assertTrue(row.totp.startsWith("otpauth://"))
        assertEquals("Work", row.folder)
        assertTrue(row.favorite)
    }

    @Test
    fun `quoted commas and embedded newlines survive parsing`() {
        val csv = "name,url,username,password\n\"Bank, Inc\",https://bank.example,me@example.com,\"p,a\nss\""

        val parsed = PasswordImport.parse(csv)

        val row = parsed.rows.first()
        assertEquals("Bank, Inc", row.title)
        assertEquals("p,a\nss", row.password)
    }

    @Test
    fun `semicolon separated exports are detected`() {
        val csv = "Name;URL;Username;Password\nMail;https://mail.example;user@example.com;hunter2"

        val parsed = PasswordImport.parse(csv)

        assertEquals(1, parsed.rows.size)
        assertEquals("Mail", parsed.rows.first().title)
        assertEquals("hunter2", parsed.rows.first().password)
    }

    @Test
    fun `utf8 bom does not leak into the first header`() {
        val csv = "\uFEFFname,url,username,password\nExample,https://example.com,user,pw"

        val parsed = PasswordImport.parse(csv)

        assertEquals("Example", parsed.rows.first().title)
    }

    @Test
    fun `lastpass grouping and extra columns are recognised`() {
        val csv = "url,username,password,totp,extra,name,grouping,fav\n" +
            "https://x.example,user,pw,,some note,My Site,Personal,0"

        val parsed = PasswordImport.parse(csv)

        assertEquals("LastPass", parsed.provider)
        val row = parsed.rows.first()
        assertEquals("My Site", row.title)
        assertEquals("some note", row.notes)
        assertEquals("Personal", row.folder)
        assertFalse(row.favorite)
    }

    @Test
    fun `empty rows are skipped instead of imported`() {
        val csv = "name,url,username,password\nReal,https://real.example,u,p\n,,,\n"

        val parsed = PasswordImport.parse(csv)

        assertEquals(1, parsed.rows.size)
        assertTrue(parsed.skipped >= 1)
    }

    @Test
    fun `unknown header format is rejected with a helpful error`() {
        assertThrows(PasswordImport.Unrecognized::class.java) {
            PasswordImport.parse("alpha,beta,gamma\n1,2,3")
        }
    }

    @Test
    fun `header without any password column is rejected`() {
        assertThrows(PasswordImport.Unrecognized::class.java) {
            PasswordImport.parse("name,url,username\nExample,https://e.x,user")
        }
    }
}
