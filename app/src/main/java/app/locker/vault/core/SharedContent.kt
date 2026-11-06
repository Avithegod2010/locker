package app.locker.vault.core

import android.content.Intent
import android.net.Uri

data class SharedContent(
    val text: String?,
    val uris: List<Uri>,
    val mimeTypes: List<String>
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && uris.isEmpty()
}

fun Intent.toSharedContent(): SharedContent? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val text = getStringExtra(Intent.EXTRA_TEXT)
    val uris = mutableListOf<Uri>()
    val mimeTypes = mutableListOf<String>()

    @Suppress("DEPRECATION")
    val stream = getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    if (stream != null) uris += stream

    clipData?.let { clip ->
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            item.uri?.let { uris += it }
            item.text?.takeIf { text.isNullOrBlank() }?.let { mimeTypes += "text/plain" }
        }
    }
    if (type != null) mimeTypes += type.orEmpty()
    val cleanUris = uris.distinct()
    if (text.isNullOrBlank() && cleanUris.isEmpty()) return null
    return SharedContent(
        text = text?.takeIf { it.isNotBlank() },
        uris = cleanUris,
        mimeTypes = mimeTypes.distinct()
    )
}
