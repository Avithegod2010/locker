package app.locker.vault.data

enum class VaultType {
    SECRET, NOTE, THING, FILE, PHOTO, VIDEO;

    val label: String
        get() = when (this) {
            SECRET -> "Secret"
            NOTE -> "Note"
            THING -> "Thing"
            FILE -> "File"
            PHOTO -> "Photo"
            VIDEO -> "Video"
        }
}

data class CustomField(
    val label: String,
    val value: String,
    val sensitive: Boolean = false
)

data class VaultItem(
    val id: String,
    val type: VaultType,
    val title: String,
    val body: String = "",
    val username: String = "",
    val password: String = "",
    val location: String = "",
    val website: String = "",
    val otpSecret: String = "",
    val ocrText: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val wrappedFileKey: String? = null,
    val hasThumbnail: Boolean = false,
    val contentHash: String = "",
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val fields: List<CustomField> = emptyList(),
    val collectionIds: List<String> = emptyList(),
    val parentId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,
    val deletedAt: Long? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/") || (type == VaultType.PHOTO && mimeType.isBlank())

    val isVideo: Boolean
        get() = type == VaultType.VIDEO || mimeType.startsWith("video/")

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/")

    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true)

    val isFileBacked: Boolean
        get() = type == VaultType.PHOTO || type == VaultType.FILE || type == VaultType.VIDEO

    val subtitle: String
        get() = when (type) {
            VaultType.SECRET -> website.ifBlank { username.ifBlank { "Password" } }
            VaultType.NOTE -> body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            VaultType.THING -> location.ifBlank { "Saved thing" }
            VaultType.FILE, VaultType.PHOTO, VaultType.VIDEO -> buildString {
                if (isVideo) append("VIDEO")
                else if (mimeType.isNotBlank()) append(mimeType.substringAfter('/').uppercase())
                if (sizeBytes > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(app.locker.vault.core.Formatting.bytes(sizeBytes))
                }
            }
        }
}

data class VaultCollection(
    val id: String,
    val name: String,
    val color: Long,
    val icon: String,
    val parentId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class LegacyContact(
    val id: String,
    val name: String,
    val relationship: String,
    val contact: String,
    val notes: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Encrypted, local-only profile. Never written to preferences in the clear. */
data class VaultProfile(
    val name: String = "Locker",
    val email: String = "Local vault"
)
