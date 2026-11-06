package app.locker.vault

import app.locker.vault.core.CalculatorEngine
import app.locker.vault.core.CalculatorState
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEngineTest {

    private fun press(vararg keys: String): String {
        var state = CalculatorState.INITIAL
        keys.forEach { key -> state = CalculatorEngine.press(state, key) }
        return state.display
    }

    @Test
    fun `adds and chains operations`() {
        assertEquals("5", press("2", "+", "3", "="))
        assertEquals("10", press("2", "+", "3", "×", "2", "="))
    }

    @Test
    fun `a typed secret code shows itself after equals`() {
        assertEquals("1234", press("1", "2", "3", "4", "="))
    }

    @Test
    fun `division by zero shows an error`() {
        assertEquals("Error", press("8", "÷", "0", "="))
    }

    @Test
    fun `clear resets the calculator`() {
        assertEquals("0", press("9", "9", "AC"))
    }

    @Test
    fun `decimals format without trailing zeros`() {
        assertEquals("1.5", press("1", ".", "5"))
        assertEquals("3", press("1", ".", "5", "+", "1", ".", "5", "="))
    }

    @Test
    fun `typing after equals starts a new number`() {
        assertEquals("7", press("2", "+", "3", "=", "7"))
    }
}
