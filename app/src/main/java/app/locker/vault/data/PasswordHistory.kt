package app.locker.vault.data

/**
 * Pure helpers for the encrypted password history. Keeping this out of the repository makes
 * the retention rules easy to unit test without Android or SQLite.
 */
object PasswordHistory {
    const val MAX_ENTRIES = 10

    /**
     * Adds the password that is being replaced, newest first. Blank values and consecutive
     * duplicates are ignored, and only the newest [MAX_ENTRIES] entries are retained.
     */
    fun append(
        history: List<PasswordHistoryEntry>,
        previousPassword: String,
        changedAt: Long
    ): List<PasswordHistoryEntry> {
        if (previousPassword.isBlank()) return history
        if (history.firstOrNull()?.password == previousPassword) return history
        return (listOf(PasswordHistoryEntry(previousPassword, changedAt)) + history)
            .filter { it.password.isNotBlank() }
            .distinctBy { it.password }
            .take(MAX_ENTRIES)
    }
}
