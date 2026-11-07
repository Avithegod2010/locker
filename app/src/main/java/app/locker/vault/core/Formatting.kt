package app.locker.vault.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatting {
    private val dateTime = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
    private val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun fullDate(epochMillis: Long): String = dateTime.format(Date(epochMillis))

    fun dateOnly(epochMillis: Long): String = date.format(Date(epochMillis))

    fun relativeDate(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
            diff < 86_400_000 -> "${TimeUnit.MILLISECONDS.toHours(diff)} h ago"
            diff < 2 * 86_400_000L -> "Yesterday, ${time.format(Date(epochMillis))}"
            diff < 7 * 86_400_000L -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
            else -> date.format(Date(epochMillis))
        }
    }

    fun bytes(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) {
            "${size} B"
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
        }
    }

    fun photoTitle(epochMillis: Long = System.currentTimeMillis()): String {
        val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "Photo_${nameFormat.format(Date(epochMillis))}"
    }
}
