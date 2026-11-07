package app.locker.vault.ui.screens.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locker.vault.ui.components.SecureWindow

/**
 * A plausible, fully usable notepad disguise. Drafts live only in memory (never written to
 * disk). Long-pressing the title opens the passcode prompt that reveals Locker's lock screen.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesDisguiseScreen(onUnlock: () -> Unit) {
    SecureWindow()
    var title by rememberSaveable { mutableStateOf("Shopping list") }
    var body by rememberSaveable {
        mutableStateOf("Milk\nEggs\nBread\nCoffee beans\nLaundry detergent\n\nCall the plumber on Tuesday")
    }
    var showPasscode by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Notes",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showPasscode = true }
                    )
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search notes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { title = ""; body = "" }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "New note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = body,
                onValueChange = { body = it.take(8000) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            if (body.isBlank() && title.isBlank()) {
                Text(
                    "Start typing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Drafts are kept on this screen only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }

    if (showPasscode) {
        DisguisePasscodeDialog(
            title = "Locked note",
            onDismiss = { showPasscode = false },
            onUnlocked = {
                showPasscode = false
                onUnlock()
            }
        )
    }
}
