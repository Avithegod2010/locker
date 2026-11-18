package app.locker.vault

import app.locker.vault.core.AutoCaptureAction
import app.locker.vault.core.AutoCaptureGate
import app.locker.vault.core.AutoCaptureState
import app.locker.vault.data.PageQuad
import app.locker.vault.data.QuadPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureGateTest {
    private fun quad(offset: Float = 0f) = PageQuad(
        topLeft = QuadPoint(20f + offset, 20f),
        topRight = QuadPoint(180f + offset, 22f),
        bottomRight = QuadPoint(178f + offset, 240f),
        bottomLeft = QuadPoint(18f + offset, 238f)
    )

    private fun feed(
        frames: List<Pair<Long, PageQuad?>>,
        start: AutoCaptureState = AutoCaptureState(),
        width: Int = 200
    ): Pair<AutoCaptureState, List<AutoCaptureAction>> {
        var state = start
        val actions = mutableListOf<AutoCaptureAction>()
        frames.forEach { (now, q) ->
            val (next, action) = AutoCaptureGate.update(state, q, now, width)
            state = next
            actions += action
        }
        return state to actions
    }

    @Test
    fun aSteadyPageFiresAfterTheHold() {
        val frames = listOf(0L to quad(), 500L to quad(), 1_100L to quad(), 1_250L to quad())
        val (_, actions) = feed(frames)
        assertEquals(AutoCaptureAction.ARMED, actions[0])
        assertEquals(AutoCaptureAction.ARMED, actions[2])
        assertEquals(AutoCaptureAction.CAPTURED, actions[3])
    }

    @Test
    fun itFiresOnlyOnce() {
        val (state, actions) = feed(listOf(0L to quad(), 2_000L to quad(), 2_100L to quad()))
        assertEquals(AutoCaptureAction.CAPTURED, actions[1])
        assertTrue(state.fired)
        assertEquals(AutoCaptureAction.WAITING, actions[2])
    }

    @Test
    fun movementResetsTheWindow() {
        val frames = listOf(
            0L to quad(),
            600L to quad(),
            900L to quad(offset = 40f),
            1_200L to quad(offset = 40f),
            1_600L to quad(offset = 40f)
        )
        val (_, actions) = feed(frames)
        // After the jump at 900 the hold restarts, so nothing fires by 1600 (only 700 ms stable).
        assertTrue(actions.none { it == AutoCaptureAction.CAPTURED })
        assertEquals(AutoCaptureAction.ARMED, actions.last())
    }

    @Test
    fun losingThePageClearsTheArmedState() {
        val (armed, first) = AutoCaptureGate.update(AutoCaptureState(), quad(), 0L, 200)
        assertEquals(AutoCaptureAction.ARMED, first)

        val (cleared, missing) = AutoCaptureGate.update(armed, null, 800L, 200)
        assertEquals(AutoCaptureAction.WAITING, missing)
        assertEquals(AutoCaptureState(), cleared)

        val (again, resumed) = AutoCaptureGate.update(cleared, quad(), 900L, 200)
        assertEquals(AutoCaptureAction.ARMED, resumed)
        assertFalse(again.fired)
        assertEquals(900L, again.stableSince)
    }

    @Test
    fun tinyDriftIsToleratedButLargeIsNot() {
        // 2 px of drift on a 200 px frame is 0.01, under the 0.035 threshold.
        val (_, small) = feed(listOf(0L to quad(), 1_300L to quad(offset = 2f)))
        assertEquals(AutoCaptureAction.CAPTURED, small.last())

        // 20 px is 0.1, well over the threshold.
        val (_, large) = feed(listOf(0L to quad(), 1_300L to quad(offset = 20f)))
        assertTrue(large.none { it == AutoCaptureAction.CAPTURED })
    }

    @Test
    fun aZeroWidthFrameIsIgnored() {
        val (state, action) = AutoCaptureGate.update(AutoCaptureState(), quad(), 5_000L, 0)
        assertEquals(AutoCaptureAction.WAITING, action)
        assertEquals(-1L, state.stableSince)
    }

    @Test
    fun driftMeasuresTheWorstCorner() {
        val a = quad()
        val b = PageQuad(
            topLeft = a.topLeft,
            topRight = a.topRight,
            bottomRight = QuadPoint(a.bottomRight.x + 7f, a.bottomRight.y - 3f),
            bottomLeft = a.bottomLeft
        )
        assertEquals(7f, AutoCaptureGate.drift(a, b), 0.001f)
    }

    @Test
    fun resetAllowsTheNextPage() {
        val fired = AutoCaptureState(lastQuad = quad(), fired = true)
        val (reset, action) = AutoCaptureGate.update(
            AutoCaptureGate.reset(),
            quad(),
            10_000L,
            200
        )
        assertEquals(AutoCaptureAction.ARMED, action)
        assertFalse(reset.fired)
        assertTrue(fired.fired)
    }
}
