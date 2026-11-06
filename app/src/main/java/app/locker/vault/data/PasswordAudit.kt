package app.locker.vault.data

import java.util.concurrent.TimeUnit

data class PasswordIssue(
    val item: VaultItem,
    val reasons: List<String>
)

object PasswordAudit {
    private val common = setOf(
        "123456", "123456789", "qwerty", "password", "111111", "123123",
        "abc123", "password1", "iloveyou", "admin", "welcome", "monkey",
        "letmein", "dragon", "football", "master", "sunshine", "princess",
        "login", "passw0rd", "starwars", "qwerty123", "12345678", "000000",
        "1234567890", "zaq12wsx", "trustno1", "whatever", "superman",
        "batman", "shadow", "michael", "jennifer", "daniel", "charlie",
        "donald", "freedom", "hello", "chocolate", "computer", "secret"
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
            if (item.password.lowercase() in common) reasons += "Common password"
            if (counts[item.password].orZero() > 1) reasons += "Reused"
            if (item.password.none { it.isDigit() } || item.password.none { it.isLetter() }) {
                reasons += "Low variety"
            }
            if (now - item.updatedAt > year) reasons += "Older than a year"
            if (reasons.isEmpty()) null else PasswordIssue(item, reasons)
        }.sortedByDescending { it.reasons.size }
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
            (it.password.length < 12 || it.password.lowercase() in common)
    }

    fun oldCount(items: List<VaultItem>): Int = items.count {
        it.type == VaultType.SECRET && it.password.isNotBlank() &&
            System.currentTimeMillis() - it.updatedAt > TimeUnit.DAYS.toMillis(365)
    }

    private fun Int?.orZero(): Int = this ?: 0
}
