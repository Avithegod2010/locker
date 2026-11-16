package app.locker.vault.core

/**
 * How long a trial vault stays "fresh".
 *
 * The countdown is a nudge, never a destructive timer: Locker does not delete anything when it
 * runs out. An expired trial keeps every item and simply asks the owner to either set a real
 * password or delete the vault explicitly.
 */
object TrialWindow {
    const val DEFAULT_DAYS = 7

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Epoch millis at which the trial becomes expired, or 0 when it never started. */
    fun endsAt(startedAt: Long, days: Int = DEFAULT_DAYS): Long {
        if (startedAt <= 0L) return 0L
        return startedAt + days.coerceAtLeast(1) * DAY_MS
    }

    /**
     * Whole days remaining, rounded up, so the last partial day still shows "1 day left".
     * Returns 0 once the window has passed and 0 when the trial never started.
     */
    fun daysLeft(
        startedAt: Long,
        now: Long = System.currentTimeMillis(),
        days: Int = DEFAULT_DAYS
    ): Long {
        val end = endsAt(startedAt, days)
        if (end <= 0L) return 0L
        val remaining = end - now
        if (remaining <= 0L) return 0L
        return (remaining + DAY_MS - 1) / DAY_MS
    }

    fun isExpired(
        startedAt: Long,
        now: Long = System.currentTimeMillis(),
        days: Int = DEFAULT_DAYS
    ): Boolean = startedAt > 0L && now >= endsAt(startedAt, days)

    /** Short label for the trial banner: "3 days left", "1 day left", "Expired". */
    fun label(
        startedAt: Long,
        now: Long = System.currentTimeMillis(),
        days: Int = DEFAULT_DAYS
    ): String {
        if (startedAt <= 0L) return "Trial vault"
        val left = daysLeft(startedAt, now, days)
        return when {
            left <= 0L -> "Trial expired"
            left == 1L -> "1 day left"
            else -> "$left days left"
        }
    }
}
