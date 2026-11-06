package app.locker.vault.data

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.zip.ZipInputStream

/**
 * Offline importer for exports from other password managers. Parsing is fully local:
 * the file the user picks through the system document picker is read, parsed in memory, and
 * every row is turned into an encrypted Secret. Nothing is uploaded anywhere.
 */
object PasswordImport {
    const val GENERIC_PROVIDER = "Generic export"

    data class Row(
        val title: String,
        val username: String,
        val password: String,
        val website: String,
        val notes: String,
        val totp: String,
        val folder: String,
        val favorite: Boolean
    )

    data class Parsed(
        val provider: String,
        val rows: List<Row>,
        val skipped: Int
    )

    class Unrecognized(val reason: String) : Exception(reason)

    /** Parses CSV, KeePass XML, or a 1Password .1pux (ZIP) export. */
    fun parseBytes(bytes: ByteArray): Parsed {
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()
        ) {
            return parse1Pux(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trimStart()
        if (text.startsWith("<") && text.contains("KeePassFile", ignoreCase = true)) {
            return parseKeePassXml(text)
        }
        return parse(text)
    }

    private fun parseKeePassXml(text: String): Parsed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(text))
        val groups = mutableListOf<String>()
        val entry = mutableMapOf<String, String>()
        var inEntry = false
        var inString = false
        var pendingKey = ""
        var textBuffer = ""
        var skipped = 0
        val rows = mutableListOf<Row>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    textBuffer = ""
                    when (parser.name) {
                        "Group" -> groups.add("")
                        "Entry" -> {
                            inEntry = true
                            entry.clear()
                        }
                        "String" -> inString = true
                    }
                }
                XmlPullParser.TEXT -> textBuffer += parser.text.orEmpty()
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Name" -> if (!inEntry && groups.isNotEmpty()) {
                        groups[groups.lastIndex] = textBuffer.trim()
                    }
                    "Key" -> pendingKey = textBuffer.trim()
                    "Value" -> if (inString && pendingKey.isNotBlank()) {
                        entry[pendingKey] = textBuffer
                    }
                    "String" -> inString = false
                    "Entry" -> {
                        inEntry = false
                        val title = entry["Title"].orEmpty()
                        val username = entry["UserName"].orEmpty()
                        val password = entry["Password"].orEmpty()
                        val url = entry["URL"].orEmpty()
                        val notes = entry["Notes"].orEmpty()
                        val totp = entry["TOTP"] ?: entry["OTP"] ?: entry["otp"] ?: ""
                        if (title.isBlank() && username.isBlank() && password.isBlank() && url.isBlank()) {
                            skipped++
                        } else {
                            rows += Row(
                                title = title.trim(),
                                username = username.trim(),
                                password = password,
                                website = url.trim(),
                                notes = notes,
                                totp = totp.trim(),
                                folder = groups.lastOrNull().orEmpty(),
                                favorite = false
                            )
                        }
                    }
                    "Group" -> if (groups.isNotEmpty()) groups.removeAt(groups.lastIndex)
                }
            }
            event = parser.next()
        }
        if (rows.isEmpty()) {
            throw Unrecognized("The KeePass export did not contain any entries.")
        }
        return Parsed(provider = "KeePass (XML)", rows = rows, skipped = skipped)
    }

    private fun parse1Pux(bytes: ByteArray): Parsed {
        val text = runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "export.data" || entry.name.endsWith(".data")) {
                        return@use zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }.getOrNull() ?: throw Unrecognized(
            "This ZIP archive is not a 1Password .1pux export."
        )

        val root = runCatching { JSONObject(text) }.getOrElse {
            throw Unrecognized("The 1Password export data could not be read.")
        }
        val rows = mutableListOf<Row>()
        var skipped = 0
        val accounts = root.optJSONArray("accounts") ?: JSONArray()
        for (accountIndex in 0 until accounts.length()) {
            val account = accounts.optJSONObject(accountIndex) ?: continue
            val vaults = account.optJSONArray("vaults") ?: continue
            for (vaultIndex in 0 until vaults.length()) {
                val vault = vaults.optJSONObject(vaultIndex) ?: continue
                val vaultName = vault.optString("name").ifBlank {
                    vault.optJSONObject("attrs")?.optString("name").orEmpty()
                }
                val items = vault.optJSONArray("items") ?: continue
                for (itemIndex in 0 until items.length()) {
                    val item = items.optJSONObject(itemIndex) ?: continue
                    val overview = item.optJSONObject("overview") ?: JSONObject()
                    val details = item.optJSONObject("details") ?: JSONObject()
                    val title = overview.optString("title")
                    val website = overview.optString("url").ifBlank {
                        overview.optJSONArray("urls")
                            ?.optJSONObject(0)
                            ?.optString("url")
                            .orEmpty()
                    }
                    var username = ""
                    var password = ""
                    details.optJSONArray("loginFields")?.let { fields ->
                        for (fieldIndex in 0 until fields.length()) {
                            val field = fields.optJSONObject(fieldIndex) ?: continue
                            when (field.optString("designation")) {
                                "username" -> username = field.optString("value")
                                "password" -> password = field.optString("value")
                            }
                        }
                    }
                    val notes = details.optString("notesPlain")
                    if (title.isBlank() && username.isBlank() && password.isBlank() && website.isBlank()) {
                        skipped++
                        continue
                    }
                    rows += Row(
                        title = title,
                        username = username,
                        password = password,
                        website = website,
                        notes = notes,
                        totp = "",
                        folder = vaultName,
                        favorite = false
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            throw Unrecognized("The 1Password export did not contain any logins.")
        }
        return Parsed(provider = "1Password (1PUX)", rows = rows, skipped = skipped)
    }

    /**
     * Column-name aliases across known managers (Bitwarden, 1Password, LastPass, KeePassXC,
     * Chrome/Edge, Samsung Internet, Firefox, Dashlane, Proton Pass and other CSV exports).
     */
    private val titleAliases = listOf("name", "title", "item name", "entry", "label")
    private val userAliases = listOf(
        "username", "user name", "user", "login", "login id", "email", "e-mail",
        "userid", "user_id", "account", "login_username", "email address"
    )
    private val passAliases = listOf("password", "passwd", "pass", "pwd", "login_password", "passwords")
    private val urlAliases = listOf(
        "url", "website", "web site", "websites", "uri", "login_uri", "site", "hostname", "url field"
    )
    private val noteAliases = listOf("notes", "note", "comment", "comments", "extra", "extra field", "details", "remarks")
    private val totpAliases = listOf("totp", "otp", "otpauth", "totp_secret", "totp secret", "login_totp", "totp uri", "2fa", "2fa key")
    private val folderAliases = listOf("folder", "group", "grouping", "category", "collection", "vault")
    private val favAliases = listOf("favorite", "favourite", "fav", "star", "starred")

    private fun headerIndex(headers: List<String>, aliases: List<String>): Int {
        val lowered = headers.map { it.trim().lowercase().removePrefix("\"").removeSuffix("\"") }
        // Exact names only: substring matching is too easy to misdetect ("hostname" contains
        // "name", for example). Longer multi-word aliases are allowed as substring matches.
        aliases.forEach { alias ->
            val exact = lowered.indexOf(alias)
            if (exact >= 0) return exact
        }
        aliases.filter { it.length >= 6 }.forEach { alias ->
            val contains = lowered.indexOfFirst { it.contains(alias) }
            if (contains >= 0) return contains
        }
        return -1
    }

    fun parse(text: String): Parsed {
        val rows = parseCsv(text.trimStart('\uFEFF')) // strip UTF-8 BOM
        if (rows.isEmpty()) throw Unrecognized("The file did not contain any rows.")

        val header = rows.first().map { it.trim() }
        val looksLikeHeader = (headerIndex(header, passAliases) >= 0) ||
            (headerIndex(header, userAliases) >= 0 && headerIndex(header, titleAliases) >= 0)

        val dataRows = if (looksLikeHeader) rows.drop(1) else rows
        if (dataRows.isEmpty()) throw Unrecognized("The export only contained a header row.")

        if (!hasPasswordColumnAliases(header)) {
            // No recognizable header at all: cannot map columns safely.
            val sample = header.take(3).joinToString(", ")
            throw Unrecognized(
                "This CSV export could not be recognised. Columns such as \"$sample\" " +
                    "did not include a password field."
            )
        }

        val titleIx = headerIndex(header, titleAliases)
        val userIx = headerIndex(header, userAliases)
        val passIx = headerIndex(header, passAliases)
        val urlIx = headerIndex(header, urlAliases)
        val noteIx = headerIndex(header, noteAliases)
        val totpIx = headerIndex(header, totpAliases)
        val folderIx = headerIndex(header, folderAliases)
        val favIx = headerIndex(header, favAliases)

        val provider = detectProvider(header)

        var skipped = 0
        val listed = dataRows.mapNotNull { cells ->
            fun at(index: Int): String = if (index in cells.indices) cells[index].trim() else ""
            val row = Row(
                title = at(titleIx),
                username = at(userIx),
                password = at(passIx),
                website = at(urlIx),
                notes = at(noteIx),
                totp = at(totpIx),
                folder = at(folderIx),
                favorite = if (favIx >= 0 && at(favIx).equals("true", ignoreCase = true) || at(favIx) == "1") true else false
            )
            if (row.password.isBlank() && row.username.isBlank() && row.title.isBlank() && row.website.isBlank()) {
                skipped++
                null
            } else {
                row
            }
        }
        if (listed.isEmpty()) {
            throw Unrecognized("No rows with a username or password were found in this file.")
        }
        return Parsed(provider = provider, rows = listed, skipped = skipped)
    }

    private fun hasPasswordColumnAliases(header: List<String>): Boolean =
        headerIndex(header, passAliases) >= 0

    private fun detectProvider(header: List<String>): String {
        val joined = header.joinToString(" ").lowercase()
        return when {
            "login_uri" in joined -> "Bitwarden"
            "grouping" in joined && "extra" in joined -> "LastPass"
            "hostname" in joined && "timecreated" in joined -> "Firefox"
            "title" in joined && "url" in joined && "username" in joined -> "1Password"
            "group" in joined && "totp" in joined -> "KeePassXC"
            "name" in joined && "url" in joined && "username" in joined -> "Chrome / Edge"
            "userid" in joined && "passwd" in joined -> "Samsung Internet"
            "username" in joined && "password" in joined -> GENERIC_PROVIDER
            else -> GENERIC_PROVIDER
        }
    }

    /** Delimiters commonly exported: comma first, semicolon for the European Excel style. */
    fun parseCsv(text: String): List<List<String>> {
        val delimiter = run {
            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        }
        val rows = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var index = 0

        var fileHasQuotes = text.contains('"')

        fun endField() {
            current += field.toString()
            field = StringBuilder()
        }

        fun endRow() {
            endField()
            if (current.size > 1 || current.first().isNotBlank()) {
                rows += current
            }
            current = mutableListOf()
        }

        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '"' && fileHasQuotes -> {
                    if (inQuotes) {
                        if (index + 2 <= text.length && text[index + 1] == '"') {
                            field.append('"')
                            index += 2
                            continue
                        }
                        inQuotes = false
                        index++
                    } else {
                        inQuotes = true
                        index++
                    }
                }
                ch == delimiter && !inQuotes -> {
                    endField()
                    index++
                }
                (ch == '\r' || ch == '\n') && !inQuotes -> {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index += 2
                    } else {
                        index++
                    }
                    endRow()
                }
                else -> {
                    field.append(ch)
                    index++
                }
            }
        }
        if (field.isNotEmpty() || current.isNotEmpty()) endRow()
        return rows
    }
}
