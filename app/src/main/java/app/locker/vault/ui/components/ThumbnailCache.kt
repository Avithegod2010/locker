package app.locker.vault.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import app.locker.vault.data.VaultItem

/** In-memory only thumbnail cache; wiped whenever the vault locks. */
object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(item: VaultItem): Bitmap? = cache.get(item.id)

    fun put(item: VaultItem, bitmap: Bitmap) {
        cache.put(item.id, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
