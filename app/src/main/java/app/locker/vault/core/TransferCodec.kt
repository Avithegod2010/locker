package app.locker.vault.core

import app.locker.vault.data.CustomField
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Encrypted, self-contained QR payload for moving a text item between two offline devices.
 * The JSON is encrypted with AES-256-GCM under an Argon2id-derived one-time passphrase.
 * Files and photos are deliberately not supported because a QR frame cannot hold them.
 */
object TransferCodec {
    private const val PREFIX = "LKRQR1:"
    private const val MAX_QR_CHARS = 2_600
    private val AAD = "locker:qr-transfer:v1".toByteArray()

    class TooLarge(val characters: Int) :
        Exception("This item is too large for a QR code ($characters characters). Use an encrypted .locker backup for large items.")

    class InvalidPayload(message: String) : Exception(message)

    fun encode(item: VaultItem, passphrase: CharArray): String {
        require(item.type != VaultType.FILE && item.type != VaultType.PHOTO && item.type != VaultType.VIDEO) {
            "Only text items can be transferred by QR"
        }
        require(passphrase.size >= 8) { "Use at least 8 characters for the transfer passphrase" }
        val json = JSONObject()
            .put("type", item.type.name)
            .put("title", item.title)
            .put("body", item.body)
            .put("username", item.username)
            .put("password", item.password)
            .put("location", item.location)
            .put("website", item.website)
            .put("otpSecret", item.otpSecret)
            .put("expiresAt", item.expiresAt)
            .put("requireBiometric", item.requireBiometric)
            .put("tags", JSONArray(item.tags))
            .put(
                "fields",
                JSONArray().apply {
                    item.fields.forEach { field ->
                        put(
                            JSONObject()
                                .put("label", field.label)
                                .put("value", field.value)
                                .put("sensitive", field.sensitive)
                        )
                    }
                }
            )
        val salt = Crypto.randomBytes(16)
        val key = Crypto.deriveKey(passphrase, salt)
        val blob = try {
            Crypto.encrypt(key, json.toString().toByteArray(Charsets.UTF_8), AAD)
        } finally {
            Crypto.wipe(key)
        }
        val payload = PREFIX + Crypto.b64encode(salt + blob)
        if (payload.length > MAX_QR_CHARS) throw TooLarge(payload.length)
        return payload
    }

    fun decode(payload: String, passphrase: CharArray): VaultItem {
        val text = payload.trim()
        if (!text.startsWith(PREFIX)) {
            throw InvalidPayload("This QR code is not a Locker transfer.")
        }
        val raw = Crypto.b64decode(text.removePrefix(PREFIX))
            ?: throw InvalidPayload("The transfer payload is damaged.")
        if (raw.size < 17) throw InvalidPayload("The transfer payload is damaged.")
        val salt = raw.copyOfRange(0, 16)
        val blob = raw.copyOfRange(16, raw.size)
        val key = Crypto.deriveKey(passphrase, salt)
        val plaintext = try {
            Crypto.decrypt(key, blob, AAD)
        } finally {
            Crypto.wipe(key)
        } ?: throw InvalidPayload("Wrong passphrase, or the QR code was modified.")
        val json = runCatching { JSONObject(String(plaintext, Charsets.UTF_8)) }.getOrElse {
            throw InvalidPayload("The transfer contents could not be read.")
        }
        val type = runCatching { VaultType.valueOf(json.optString("type")) }.getOrElse {
            throw InvalidPayload("The transfer has an unknown item type.")
        }
        val fields = mutableListOf<CustomField>()
        json.optJSONArray("fields")?.let { array ->
            for (i in 0 until array.length()) {
                val field = array.optJSONObject(i) ?: continue
                val label = field.optString("label")
                if (label.isNotBlank()) {
                    fields += CustomField(
                        label = label,
                        value = field.optString("value"),
                        sensitive = field.optBoolean("sensitive", false)
                    )
                }
            }
        }
        val tags = mutableListOf<String>()
        json.optJSONArray("tags")?.let { array ->
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(tags::add)
            }
        }
        val now = System.currentTimeMillis()
        return VaultItem(
            id = UUID.randomUUID().toString(),
            type = type,
            title = json.optString("title"),
            body = json.optString("body"),
            username = json.optString("username"),
            password = json.optString("password"),
            location = json.optString("location"),
            website = json.optString("website"),
            otpSecret = json.optString("otpSecret"),
            tags = tags,
            fields = fields,
            createdAt = now,
            updatedAt = now,
            expiresAt = json.optLong("expiresAt", 0L),
            requireBiometric = json.optBoolean("requireBiometric", false)
        )
    }

    fun looksLikeTransfer(text: String): Boolean = text.trim().startsWith(PREFIX)

    fun maxCharacters(): Int = MAX_QR_CHARS
}
