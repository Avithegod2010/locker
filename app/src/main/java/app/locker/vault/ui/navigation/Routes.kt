package app.locker.vault.ui.navigation

import app.locker.vault.data.VaultType
import android.net.Uri

object Routes {
    const val HOME = "home"
    const val COLLECTIONS = "collections"
    const val TRASH = "trash"
    const val ARCHIVE = "archive"
    const val LEGACY = "legacy"
    const val ACCOUNT = "account"
    const val SECURITY = "security"
    const val APPEARANCE = "appearance"
    const val GENERAL = "general"
    const val ABOUT = "about"
    const val BACKUP = "backup"
    const val PASSWORD_HEALTH = "password-health"
    const val CAMERA = "camera"
    const val SLIDESHOW = "slideshow"
    const val IMPORT_PHOTO = "import/photo"
    const val IMPORT_FILE = "import/file"

    const val ITEM = "item/{id}"
    const val EDIT = "edit/{id}"
    const val VIEWER = "viewer/{id}"
    const val VIDEO = "video/{id}"
    const val PDF = "pdf/{id}"
    const val EDIT_IMAGE = "edit-image/{id}"
    const val NEW = "new/{type}"

    fun item(id: String): String = "item/$id"
    fun edit(id: String): String = "edit/$id"
    fun viewer(id: String): String = "viewer/$id"
    fun video(id: String): String = "video/$id"
    fun pdf(id: String): String = "pdf/$id"
    fun editImage(id: String): String = "edit-image/$id"
    fun new(type: VaultType): String = "new/${type.name}"
}
