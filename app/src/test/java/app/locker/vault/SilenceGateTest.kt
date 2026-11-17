package app.locker.vault

import app.locker.vault.core.SilenceAction
import app.locker.vault.core.SilenceGate
import app.locker.vault.core.SilenceGateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceGateTest {
    private fun run(
        samples: List<Pair<Long, Float>>,
        start: SilenceGateState = SilenceGateState()
    ): List<SilenceAction> {
        var state = start
        val actions = mutableListOf<SilenceAction>()
        samples.forEach { (now, amplitude) ->
            val decision = SilenceGate.update(state, amplitude, now)
            state = decision.state
            actions += decision.action
        }
        return actions
    }

    @Test
    fun speechNeverPauses() {
        val actions = run(List(20) { index -> index * 100L to 0.4f })
        assertTrue(actions.all { it == SilenceAction.NONE })
    }

    @Test
    fun sustainedSilencePausesOnceAfterTheHold() {
        val actions = run(listOf(
            0L to 0.0f,
            500L to 0.0f,
            1_000L to 0.0f,
            1_300L to 0.0f,
            1_400L to 0.0f
        ))
        assertEquals(1, actions.count { it == SilenceAction.PAUSE })
        assertEquals(0, actions.count { it == SilenceAction.RESUME })
    }

    @Test
    fun aShortGapDoesNotPause() {
        val actions = run(listOf(
            0L to 0.01f,
            400L to 0.5f,
            800L to 0.01f
        ))
        assertTrue(actions.none { it == SilenceAction.PAUSE })
    }

    @Test
    fun speechResumesTheRecorder() {
        var state = SilenceGateState(autoPaused = true)
        val decision = SilenceGate.update(state, 0.3f, now = 5_000L)
        assertEquals(SilenceAction.RESUME, decision.action)
        assertFalse(decision.state.autoPaused)
        state = decision.state
        // A following quiet sample starts a fresh streak rather than pausing immediately.
        val after = SilenceGate.update(state, 0.0f, now = 5_100L)
        assertEquals(SilenceAction.NONE, after.action)
    }

    @Test
    fun theResumeThresholdIsHigherThanThePauseThreshold() {
        // A level between the two thresholds must not wake the gate: that is the anti-chatter rule.
        val paused = SilenceGateState(autoPaused = true)
        val whisper = SilenceGate.update(paused, amplitude = 0.03f, now = 1_000L)
        assertEquals(SilenceAction.NONE, whisper.action)
        assertTrue(whisper.state.autoPaused)
    }

    @Test
    fun resetClearsAutomaticPause() {
        val reset = SilenceGate.reset()
        assertFalse(reset.autoPaused)
        assertEquals(SilenceGateState.NO_STREAK, reset.quietSince)
    }

    @Test
    fun amplitudeIsClamped() {
        val actions = run(listOf(0L to 4f, 100L to -3f, 200L to 0f))
        assertEquals(3, actions.size)
        assertEquals(SilenceAction.NONE, actions.first())
    }
}
