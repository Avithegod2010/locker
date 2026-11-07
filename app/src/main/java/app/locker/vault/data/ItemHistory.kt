package app.locker.vault.data

/**
 * Keeps a small, encrypted trail of earlier versions of a secret or note. Only changes to
 * meaningful content create a version; edits to tags, collections, favourites, or an expiry
 * date do not, so the list stays useful instead of noisy.
 */
object ItemHistory {
    const val MAX_ENTRIES = 10

    fun append(
        history: List<ItemHistoryEntry>,
        previous: VaultItem,
        changedAt: Long
    ): List<ItemHistoryEntry> {
        if (history.firstOrNull()?.matches(previous) == true) return history
        val snapshot = ItemHistoryEntry(
            title = previous.title,
            body = previous.body,
            username = previous.username,
            password = previous.password,
            location = previous.location,
            website = previous.website,
            otpSecret = previous.otpSecret,
            fields = previous.fields,
            changedAt = changedAt
        )
        return (listOf(snapshot) + history).take(MAX_ENTRIES)
    }

    fun contentChanged(previous: VaultItem, next: VaultItem): Boolean =
        previous.title != next.title ||
            previous.body != next.body ||
            previous.username != next.username ||
            previous.password != next.password ||
            previous.location != next.location ||
            previous.website != next.website ||
            previous.otpSecret != next.otpSecret ||
            previous.fields != next.fields

    private fun ItemHistoryEntry.matches(item: VaultItem): Boolean =
        title == item.title &&
            body == item.body &&
            username == item.username &&
            password == item.password &&
            location == item.location &&
            website == item.website &&
            otpSecret == item.otpSecret &&
            fields == item.fields
}
