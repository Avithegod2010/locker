package app.locker.vault.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved search shown as a "smart folder". */
data class SavedSearch(
    val id: String,
    val name: String,
    val query: String,
    val createdAt: Long = System.currentTimeMillis(),
    val color: Long = 0xFF5B9CFF,
    val icon: String = "search",
    val pinned: Boolean = true
)

/**
 * Smart folders are just saved search queries. They are stored in the app's private
 * preferences as JSON; the queries themselves never leave the device and may reference
 * vault fields without containing any vault data.
 */
object SavedSearches {
    fun parse(json: String?): List<SavedSearch> = runCatching {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val query = obj.optString("query")
            if (query.isBlank()) return@mapNotNull null
            SavedSearch(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = obj.optString("name").ifBlank { query },
                query = query,
                createdAt = obj.optLong("createdAt"),
                color = obj.optLong("color", 0xFF5B9CFF),
                icon = obj.optString("icon", "search"),
                pinned = obj.optBoolean("pinned", true)
            )
        }
    }.getOrDefault(emptyList())

    fun encode(searches: List<SavedSearch>): String {
        val array = JSONArray()
        searches.forEach { search ->
            array.put(
                JSONObject()
                    .put("id", search.id)
                    .put("name", search.name)
                    .put("query", search.query)
                    .put("createdAt", search.createdAt)
                    .put("color", search.color)
                    .put("icon", search.icon)
                    .put("pinned", search.pinned)
            )
        }
        return array.toString()
    }

    fun create(name: String, query: String): SavedSearch = SavedSearch(
        id = UUID.randomUUID().toString(),
        name = name.trim().ifBlank { query.trim() },
        query = query.trim()
    )

    fun add(json: String?, name: String, query: String): String {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return json ?: "[]"
        val existing = parse(json).filterNot { it.query.equals(trimmed, ignoreCase = true) }
        return encode(existing + create(name, trimmed))
    }

    fun update(json: String?, updated: SavedSearch): String =
        encode(parse(json).map { if (it.id == updated.id) updated else it })

    fun setPinned(json: String?, id: String, pinned: Boolean): String =
        encode(parse(json).map { if (it.id == id) it.copy(pinned = pinned) else it })

    fun remove(json: String?, id: String): String =
        encode(parse(json).filterNot { it.id == id })
}
