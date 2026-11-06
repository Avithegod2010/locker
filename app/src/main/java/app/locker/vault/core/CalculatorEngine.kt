package app.locker.vault.core

import java.util.Locale
import kotlin.math.abs

/**
 * Small immediate-execution calculator used by the calculator disguise. It is deliberately
 * independent of Android so its behaviour can be unit-tested.
 */
data class CalculatorState(
    val display: String = "0",
    val accumulator: Double? = null,
    val pendingOp: Char? = null,
    val entering: Boolean = true,
    val evaluated: Boolean = false
) {
    companion object {
        val INITIAL = CalculatorState()
    }
}

object CalculatorEngine {
    private const val MAX_DIGITS = 12

    fun press(state: CalculatorState, key: String): CalculatorState = when {
        key.length == 1 && key[0] in '0'..'9' -> digit(state, key[0])
        key == "." -> dot(state)
        key == "+" || key == "−" || key == "×" || key == "÷" -> operator(state, key[0])
        key == "=" -> equals(state)
        key == "AC" -> CalculatorState.INITIAL
        key == "±" -> negate(state)
        key == "%" -> percent(state)
        key == "⌫" -> backspace(state)
        else -> state
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "Error"
        if (abs(value) >= 1_000_000_000_000.0) {
            return String.format(Locale.US, "%.4e", value)
        }
        val text = String.format(Locale.US, "%.8f", value)
            .trimEnd('0')
            .trimEnd('.')
        return if (text == "-0" || text.isEmpty()) "0" else text
    }

    private fun digit(state: CalculatorState, digit: Char): CalculatorState {
        if (state.display == "Error" || state.evaluated || !state.entering) {
            return state.copy(display = digit.toString(), entering = true, evaluated = false)
        }
        if (state.display == "0") return state.copy(display = digit.toString())
        if (state.display.count { it.isDigit() } >= MAX_DIGITS) return state
        return state.copy(display = state.display + digit)
    }

    private fun dot(state: CalculatorState): CalculatorState {
        if (state.display == "Error" || state.evaluated || !state.entering) {
            return state.copy(display = "0.", entering = true, evaluated = false)
        }
        if ('.' in state.display) return state
        return state.copy(display = state.display + ".")
    }

    private fun operator(state: CalculatorState, op: Char): CalculatorState {
        if (state.display == "Error") return CalculatorState.INITIAL
        val current = state.display.toDoubleOrNull() ?: 0.0
        val base = state.accumulator
        val raw = when (state.pendingOp) {
            '+' -> (base ?: 0.0) + current
            '−' -> (base ?: 0.0) - current
            '×' -> (base ?: 0.0) * current
            '÷' -> if (current == 0.0) Double.NaN else (base ?: 0.0) / current
            else -> current
        }
        val text = format(raw)
        return CalculatorState(
            display = text,
            accumulator = if (text == "Error") null else raw,
            pendingOp = op,
            entering = false
        )
    }

    private fun equals(state: CalculatorState): CalculatorState {
        if (state.display == "Error") return CalculatorState.INITIAL
        val current = state.display.toDoubleOrNull() ?: 0.0
        val raw = when (state.pendingOp) {
            null -> current
            '+' -> (state.accumulator ?: 0.0) + current
            '−' -> (state.accumulator ?: 0.0) - current
            '×' -> (state.accumulator ?: 0.0) * current
            '÷' -> if (current == 0.0) Double.NaN else (state.accumulator ?: 0.0) / current
            else -> current
        }
        val text = format(raw)
        return CalculatorState(
            display = text,
            accumulator = null,
            pendingOp = null,
            entering = false,
            evaluated = text != "Error"
        )
    }

    private fun negate(state: CalculatorState): CalculatorState {
        if (state.display == "Error") return CalculatorState.INITIAL
        val display = state.display
        return state.copy(
            display = if (display.startsWith("-")) display.drop(1) else "-$display"
        )
    }

    private fun percent(state: CalculatorState): CalculatorState {
        if (state.display == "Error") return CalculatorState.INITIAL
        val value = (state.display.toDoubleOrNull() ?: 0.0) / 100.0
        return state.copy(display = format(value), entering = false)
    }

    private fun backspace(state: CalculatorState): CalculatorState {
        if (state.display == "Error" || state.evaluated || !state.entering) return state
        val display = state.display.dropLast(1)
        return state.copy(display = display.ifBlank { "0" }, entering = display.isNotBlank())
    }
}
