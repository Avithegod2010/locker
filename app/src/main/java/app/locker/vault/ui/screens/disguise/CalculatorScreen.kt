package app.locker.vault.ui.screens.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locker.vault.core.CalculatorEngine
import app.locker.vault.core.CalculatorState
import app.locker.vault.ui.LocalAppContainer

private val ICON_ROWS = listOf(
    listOf("AC", "±", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+")
)

/**
 * Fully working calculator shown instead of the lock screen when the calculator disguise is
 * active. Typing the configured code and pressing "=" reveals Locker's real lock screen.
 */
@Composable
fun CalculatorScreen(onUnlock: () -> Unit) {
    val app = LocalAppContainer.current
    var state by remember { mutableStateOf(CalculatorState.INITIAL) }

    fun press(key: String) {
        val next = CalculatorEngine.press(state, key)
        state = next
        if (key == "=" && next.display != "Error") {
            if (app.preferences.calculatorCodeMatches(next.display)) {
                state = CalculatorState.INITIAL
                onUnlock()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                state.display,
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ICON_ROWS.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { label ->
                        CalcButton(label, Modifier.weight(1f)) { press(it) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcButton("0", Modifier.weight(2f)) { press(it) }
                CalcButton(".", Modifier.weight(1f)) { press(it) }
                CalcButton("=", Modifier.weight(1f)) { press(it) }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    modifier: Modifier,
    onPress: (String) -> Unit
) {
    val background = when (label) {
        "÷", "×", "−", "+", "=" -> Color(0xFFFF9F0A)
        "AC", "±", "%" -> Color(0xFFA5A5A5)
        else -> Color(0xFF333333)
    }
    val content = if (background == Color(0xFFA5A5A5)) Color.Black else Color.White
    Surface(
        onClick = { onPress(label) },
        shape = RoundedCornerShape(50),
        color = background,
        modifier = modifier.height(68.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = content, fontSize = 26.sp)
        }
    }
}
