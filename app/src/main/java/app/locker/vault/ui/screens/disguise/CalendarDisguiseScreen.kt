package app.locker.vault.ui.screens.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locker.vault.ui.components.SecureWindow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A working month calendar disguise. Long-pressing the month title opens the passcode
 * prompt that reveals Locker's lock screen.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CalendarDisguiseScreen(onUnlock: () -> Unit) {
    SecureWindow()
    val today = remember { Calendar.getInstance() }
    var month by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    var showPasscode by remember { mutableStateOf(false) }
    val monthLabel = remember(month.timeInMillis) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(month.time)
    }
    val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                monthLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showPasscode = true }
                    )
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                month = (month.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            }) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                month = (month.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            }) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Next month",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val cells = remember(month.timeInMillis) { buildMonthCells(month) }
        Column(Modifier.fillMaxWidth()) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val isCurrentMonth = day != null
                        val isToday = isCurrentMonth &&
                            day == today.get(Calendar.DAY_OF_MONTH) &&
                            month.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            month.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                        val isSelected = isCurrentMonth && day == selectedDay
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .background(
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        else -> Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .then(
                                    if (isCurrentMonth) {
                                        Modifier.clickable { selectedDay = day!! }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isCurrentMonth -> MaterialTheme.colorScheme.onBackground
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "No events for this day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No reminders are set.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(4.dp)
        )
    }

    if (showPasscode) {
        DisguisePasscodeDialog(
            title = "Calendar lock",
            onDismiss = { showPasscode = false },
            onUnlocked = {
                showPasscode = false
                onUnlock()
            }
        )
    }
}

private fun buildMonthCells(month: Calendar): List<Int?> {
    val first = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Monday-first offset
    val dayOfWeek = first.get(Calendar.DAY_OF_WEEK) // Sunday = 1
    val offset = (dayOfWeek + 5) % 7
    val cells = MutableList<Int?>(42) { null }
    for (day in 1..daysInMonth) {
        cells[offset + day - 1] = day
    }
    return cells
}
