package app.locker.vault.data

import java.util.concurrent.TimeUnit

data class PasswordIssue(
    val item: VaultItem,
    val reasons: List<String>
)

enum class ExpiryBucket { EXPIRED, SOON, LATER }

object PasswordAudit {
    const val EXPIRING_SOON_DAYS = 30

    private val common = setOf(
        "123456", "123456789", "qwerty", "password", "111111", "123123",
        "abc123", "password1", "iloveyou", "admin", "welcome", "monkey",
        "letmein", "dragon", "football", "master", "sunshine", "princess",
        "login", "passw0rd", "starwars", "qwerty123", "12345678", "000000",
        "1234567890", "zaq12wsx", "trustno1", "whatever", "superman",
        "batman", "shadow", "michael", "jennifer", "daniel", "charlie",
        "donald", "freedom", "hello", "chocolate", "computer", "secret",
        "password123", "qwertyuiop", "asdfghjkl", "zxcvbnm", "letmein123",
        "welcome1", "football1", "baseball", "soccer", "hockey", "killer",
        "george", "summer", "winter", "spring", "autumn", "january",
        "february", "march", "april", "may", "june", "july", "august",
        "september", "october", "november", "december", "monday", "tuesday",
        "wednesday", "thursday", "friday", "saturday", "sunday", "internet",
        "samsung", "google", "iphone", "android", "minecraft", "pokemon",
        "naruto", "sasuke", "liverpool", "chelsea", "arsenal", "barcelona",
        "realmadrid", "cricket", "india", "america", "canada", "london",
        "paris", "tokyo", "matrix", "access", "flower", "cookie", "banana",
        "orange", "purple", "silver", "golden", "diamond", "angel", "baby",
        "love", "lovely", "forever", "changeme", "default", "guest",
        "root", "toor", "pass", "test", "test123", "qwertz", "azerty"
    )

    private val leet = mapOf(
        '@' to 'a', '0' to 'o', '1' to 'l', '3' to 'e', '$' to 's',
        '5' to 's', '7' to 't', '!' to 'i', '4' to 'a', '+' to 't'
    )

    private val sequences = listOf(
        "0123456789", "9876543210",
        "abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba",
        "qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa",
        "zxcvbnm", "mnbvcxz"
    )

    fun audit(items: List<VaultItem>): List<PasswordIssue> {
        val secrets = items.filter {
            it.type == VaultType.SECRET && it.deletedAt == null && it.password.isNotBlank()
        }
        val counts = secrets.groupingBy { it.password }.eachCount()
        val now = System.currentTimeMillis()
        val year = TimeUnit.DAYS.toMillis(365)
        return secrets.mapNotNull { item ->
            val reasons = mutableListOf<String>()
            if (item.password.length < 12) reasons += "Short"
            if (isPredictable(item.password)) reasons += "Predictable"
            if (counts[item.password].orZero() > 1) reasons += "Reused"
            if (item.password.none { it.isDigit() } || item.password.none { it.isLetter() }) {
                reasons += "Low variety"
            }
            if (now - item.updatedAt > year) reasons += "Older than a year"
            if (reasons.isEmpty()) null else PasswordIssue(item, reasons)
        }.sortedByDescending { it.reasons.size }
    }

    /**
     * Offline strength heuristic: flags well-known passwords, keyboard walks, repeated
     * characters, simple sequences, years, and common base words with numeric suffixes.
     * No network or wordlist download is involved.
     */
    fun isPredictable(password: String): Boolean {
        if (password.isBlank()) return true
        val lower = password.lowercase()
        if (lower in common) return true

        // Plain form keeps digits for year/sequence checks; the leet form maps look-alikes.
        val plain = lower.filter { it.isLetterOrDigit() }
        if (plain.isBlank()) return false
        if (plain in common) return true
        val normalized = buildString {
            lower.forEach { ch -> append(leet[ch] ?: ch) }
        }.filter { it.isLetterOrDigit() }
        if (normalized in common) return true
        if (plain.startsWith("password") || plain.startsWith("qwerty")) return true
        if (normalized.startsWith("password") || normalized.startsWith("qwerty")) return true
        if (plain.startsWith("admin") || plain.startsWith("welcome")) return true
        if (normalized.startsWith("admin") || normalized.startsWith("welcome")) return true

        // Common base word followed by digits ("sunshine2024").
        val plainBase = plain.trimEnd { it.isDigit() }
        if (plainBase.length >= 3 && plainBase in common) return true
        val leetBase = normalized.trimEnd { it.isDigit() }
        if (leetBase.length >= 3 && leetBase in common) return true

        // Keyboard walks and alphabetic/numeric sequences.
        if (sequences.any { sequence ->
                sequence.windowed(4).any { window -> lower.contains(window) }
            }
        ) {
            return true
        }

        // Repeated characters or repeated short patterns.
        if (Regex("^(.)\\1{5,}$").matches(lower)) return true
        if (Regex("^(.{2,4})\\1{2,}$").matches(lower)) return true

        // A year on its own.
        if (Regex("(19|20)\\d{2}").matches(plain)) return true

        // Monotonic digit runs such as 123456, 987654.
        if (plain.length >= 6 && plain.all { it.isDigit() }) {
            val digits = plain.map { it - '0' }
            val ascending = digits.zipWithNext().all { (a, b) -> b == a + 1 }
            val descending = digits.zipWithNext().all { (a, b) -> b == a - 1 }
            if (ascending || descending) return true
        }

        return false
    }

    fun reusedCount(items: List<VaultItem>): Int {
        val groups = items
            .filter { it.type == VaultType.SECRET && it.password.isNotBlank() }
            .groupBy { it.password }
            .filterValues { it.size > 1 }
        return groups.values.sumOf { it.size }
    }

    fun weakCount(items: List<VaultItem>): Int = items.count {
        it.type == VaultType.SECRET && it.password.isNotBlank() &&
            (it.password.length < 12 || isPredictable(it.password))
    }

    fun oldCount(items: List<VaultItem>): Int = items.count {
        it.type == VaultType.SECRET && it.password.isNotBlank() &&
            System.currentTimeMillis() - it.updatedAt > TimeUnit.DAYS.toMillis(365)
    }

    /** Items with an expiry date that has already passed. */
    fun expired(items: List<VaultItem>, now: Long = System.currentTimeMillis()): List<VaultItem> =
        items.filter { it.deletedAt == null && it.expiresAt in 1..now }
            .sortedBy { it.expiresAt }

    /** Items expiring within the next [EXPIRING_SOON_DAYS] days. */
    fun expiringSoon(
        items: List<VaultItem>,
        now: Long = System.currentTimeMillis(),
        windowDays: Int = EXPIRING_SOON_DAYS
    ): List<VaultItem> {
        val end = now + TimeUnit.DAYS.toMillis(windowDays.toLong())
        return items.filter { it.deletedAt == null && it.expiresAt in (now + 1)..end }
            .sortedBy { it.expiresAt }
    }

    fun expiredCount(items: List<VaultItem>, now: Long = System.currentTimeMillis()): Int =
        expired(items, now).size

    fun expiringSoonCount(items: List<VaultItem>, now: Long = System.currentTimeMillis()): Int =
        expiringSoon(items, now).size

    /** Classifies an expiry date for badges and cards, or null when the item never expires. */
    fun expiryBucket(
        expiresAt: Long,
        now: Long = System.currentTimeMillis()
    ): ExpiryBucket? {
        if (expiresAt <= 0L) return null
        if (expiresAt <= now) return ExpiryBucket.EXPIRED
        val end = now + TimeUnit.DAYS.toMillis(EXPIRING_SOON_DAYS.toLong())
        return if (expiresAt <= end) ExpiryBucket.SOON else ExpiryBucket.LATER
    }

    private fun Int?.orZero(): Int = this ?: 0
}
