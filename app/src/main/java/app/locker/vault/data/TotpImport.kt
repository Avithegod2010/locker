package app.locker.vault.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * Parses `otpauth://` links and Google Authenticator `otpauth-migration://` payloads.
 * The migration payload is a small protobuf; it is decoded locally with no network access.
 */
object TotpImport {
    fun parse(content: String): List<PasswordImport.Row> {
        val text = content.trim()
        return when {
            text.startsWith("otpauth-migration://", ignoreCase = true) -> parseMigration(text)
            text.startsWith("otpauth://", ignoreCase = true) -> listOf(parseSingle(text))
            else -> emptyList()
        }
    }

    fun parseOrThrow(content: String): List<PasswordImport.Row> {
        val rows = parse(content)
        if (rows.isEmpty()) {
            throw PasswordImport.Unrecognized(
                "No TOTP QR code was found. Use a Google Authenticator export QR or an otpauth:// link."
            )
        }
        return rows
    }

    private fun parseSingle(uri: String): PasswordImport.Row {
        val withoutScheme = uri.substringAfter("otpauth://", "")
        val type = withoutScheme.substringBefore('/').lowercase()
        if (type != "totp") {
            throw PasswordImport.Unrecognized("Only time-based (TOTP) QR codes are supported.")
        }
        val body = withoutScheme.substringAfter('/')
        val label = decode(body.substringBefore('?'))
        val query = body.substringAfter('?', "")
        val params = queryParams(query)
        val secret = params["secret"]
            ?: throw PasswordImport.Unrecognized("The QR code did not contain a TOTP secret.")
        val issuer = params["issuer"].orEmpty()
        val title = when {
            label.isNotBlank() -> label
            issuer.isNotBlank() -> issuer
            else -> "Imported TOTP"
        }
        return PasswordImport.Row(
            title = title,
            username = "",
            password = "",
            website = "",
            notes = if (issuer.isNotBlank() && !label.contains(issuer)) "Issuer: $issuer" else "",
            totp = buildUri(label.ifBlank { issuer.ifBlank { "Imported TOTP" } }, issuer, secret),
            folder = "",
            favorite = false
        )
    }

    private fun parseMigration(uri: String): List<PasswordImport.Row> {
        val query = uri.substringAfter('?', "")
        val data = queryParams(query)["data"]
            ?: throw PasswordImport.Unrecognized("The migration QR code did not contain data.")
        val bytes = runCatching {
            val normalized = data.replace(' ', '+').replace('-', '+').replace('_', '/')
            Base64.getDecoder().decode(normalized)
        }.getOrElse {
            throw PasswordImport.Unrecognized("The migration QR code could not be decoded.")
        }
        val reader = ProtoReader(bytes)
        val rows = mutableListOf<PasswordImport.Row>()
        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x07
            if (field == 1 && wire == 2) {
                val entry = parseOtpParameters(reader.readBytes())
                if (entry != null) rows += entry
            } else {
                reader.skip(wire)
            }
        }
        if (rows.isEmpty()) {
            throw PasswordImport.Unrecognized(
                "The Google Authenticator QR code did not contain any TOTP accounts."
            )
        }
        return rows
    }

    private fun parseOtpParameters(bytes: ByteArray): PasswordImport.Row? {
        val reader = ProtoReader(bytes)
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = 1
        var digits = 1
        var type = 2
        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x07
            when {
                field == 1 && wire == 2 -> secret = reader.readBytes()
                field == 2 && wire == 2 -> name = reader.readBytes().toString(Charsets.UTF_8)
                field == 3 && wire == 2 -> issuer = reader.readBytes().toString(Charsets.UTF_8)
                field == 4 && wire == 0 -> algorithm = reader.readVarint().toInt()
                field == 5 && wire == 0 -> digits = reader.readVarint().toInt()
                field == 6 && wire == 0 -> type = reader.readVarint().toInt()
                else -> reader.skip(wire)
            }
        }
        // 1 = HOTP, which needs a counter; this importer only handles time-based codes.
        if (type == 1 || secret.isEmpty()) return null
        val algorithmName = when (algorithm) {
            2 -> "SHA256"
            3 -> "SHA512"
            4 -> "MD5"
            else -> "SHA1"
        }
        val digitCount = if (digits == 2) 8 else 6
        val label = when {
            issuer.isNotBlank() && name.isNotBlank() -> "$issuer:$name"
            name.isNotBlank() -> name
            issuer.isNotBlank() -> issuer
            else -> "Imported TOTP"
        }
        val encodedLabel = encode(label)
        val params = buildList {
            add("secret=${encodeBase32(secret)}")
            if (issuer.isNotBlank()) add("issuer=${encode(issuer)}")
            add("algorithm=$algorithmName")
            add("digits=$digitCount")
            add("period=30")
        }.joinToString("&")
        return PasswordImport.Row(
            title = name.ifBlank { issuer.ifBlank { "Imported TOTP" } },
            username = "",
            password = "",
            website = "",
            notes = if (issuer.isNotBlank() && name.isNotBlank()) "Issuer: $issuer" else "",
            totp = "otpauth://totp/$encodedLabel?$params",
            folder = "",
            favorite = false
        )
    }

    private fun buildUri(label: String, issuer: String, secret: String): String {
        val params = buildList {
            add("secret=$secret")
            if (issuer.isNotBlank()) add("issuer=${encode(issuer)}")
            add("period=30")
        }.joinToString("&")
        return "otpauth://totp/${encode(label)}?$params"
    }

    private fun queryParams(query: String): Map<String, String> =
        query.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val key = decode(part.substringBefore('='))
                val value = decode(part.substringAfter('=', ""))
                key to value
            }
            .toMap()

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** RFC 4648 Base32, the alphabet used by `otpauth://` and authenticator apps. */
    private fun encodeBase32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                output.append(alphabet[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) {
            output.append(alphabet[(buffer shl (5 - bits)) and 0x1F])
        }
        return output.toString()
    }

    private class ProtoReader(private val bytes: ByteArray) {
        private var position = 0

        fun hasMore(): Boolean = position < bytes.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (position >= bytes.size) error("Truncated protobuf")
                val value = bytes[position++].toInt() and 0xFF
                result = result or ((value and 0x7F).toLong() shl shift)
                if (value and 0x80 == 0) break
                shift += 7
                if (shift > 63) error("Invalid protobuf varint")
            }
            return result
        }

        fun readBytes(): ByteArray {
            val length = readVarint().toInt()
            require(length >= 0 && position + length <= bytes.size) { "Truncated protobuf field" }
            return bytes.copyOfRange(position, position + length).also { position += length }
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> position += 8
                2 -> position += readVarint().toInt()
                5 -> position += 4
                else -> error("Unsupported protobuf wire type $wireType")
            }
            require(position <= bytes.size) { "Truncated protobuf" }
        }
    }
}
