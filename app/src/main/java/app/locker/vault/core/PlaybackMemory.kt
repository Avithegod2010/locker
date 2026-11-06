package app.locker.vault.core

import android.content.Context

/**
 * Remembers playback position and speed per item. This is intentionally not encrypted:
 * it contains only opaque item ids and playback numbers, no titles or content.
 */
class PlaybackMemory(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("locker_playback", Context.MODE_PRIVATE)

    fun position(itemId: String): Long = prefs.getLong("pos_$itemId", 0L)

    fun speed(itemId: String): Float = prefs.getFloat("speed_$itemId", 1f)

    fun save(itemId: String, positionMs: Long, speed: Float) {
        prefs.edit()
            .putLong("pos_$itemId", positionMs.coerceAtLeast(0L))
            .putFloat("speed_$itemId", speed.coerceIn(0.25f, 3f))
            .apply()
    }

    fun clear(itemId: String) {
        prefs.edit().remove("pos_$itemId").remove("speed_$itemId").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
