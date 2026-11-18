package app.locker.vault.core

import app.locker.vault.data.PageQuad
import app.locker.vault.data.QuadPoint
import kotlin.math.abs
import kotlin.math.max

/** What the caller should do after feeding a frame to the gate. */
enum class AutoCaptureAction { WAITING, ARMED, CAPTURED }

data class AutoCaptureState(
    /** Monotonic time the current page shape became stable, or -1 before that. */
    val stableSince: Long = -1L,
    val lastQuad: PageQuad? = null,
    /** True once a capture has been requested; the caller resets after processing it. */
    val fired: Boolean = false
)

/**
 * Decides when a photographed page is steady enough to capture automatically.
 *
 * Two conditions must hold: a page-shaped quad is detected in the frame, and its corners stop
 * moving for [HOLD_MS]. The corner drift is normalised by frame width so the same thresholds work
 * on any camera resolution. Pure, so the timing can be unit tested without a camera.
 */
object AutoCaptureGate {
    const val HOLD_MS = 1_200L
    /** Maximum corner movement, as a fraction of frame width, that still counts as steady. */
    const val MAX_DRIFT = 0.035f

    fun reset(): AutoCaptureState = AutoCaptureState()

    fun update(
        state: AutoCaptureState,
        quad: PageQuad?,
        now: Long,
        frameWidth: Int,
        holdMs: Long = HOLD_MS,
        maxDrift: Float = MAX_DRIFT
    ): Pair<AutoCaptureState, AutoCaptureAction> {
        if (frameWidth <= 0) return state to AutoCaptureAction.WAITING
        // A single capture per steady page: wait for the caller to reset.
        if (state.fired) return state to AutoCaptureAction.WAITING
        if (quad == null) {
            return AutoCaptureState() to AutoCaptureAction.WAITING
        }

        val previous = state.lastQuad
        if (previous != null) {
            val drift = drift(previous, quad) / frameWidth.toFloat()
            if (drift > maxDrift) {
                // The scene moved: start the steady window again from this frame.
                return AutoCaptureState(stableSince = now, lastQuad = quad) to
                    AutoCaptureAction.WAITING
            }
        }

        val since = if (state.stableSince < 0L) now else state.stableSince
        if (now - since >= holdMs.coerceAtLeast(0L)) {
            return AutoCaptureState(stableSince = since, lastQuad = quad, fired = true) to
                AutoCaptureAction.CAPTURED
        }
        return AutoCaptureState(stableSince = since, lastQuad = quad) to
            AutoCaptureAction.ARMED
    }

    /** Largest distance between matching corners of two quads, in frame pixels. */
    fun drift(a: PageQuad, b: PageQuad): Float {
        fun distance(p: QuadPoint, q: QuadPoint) = max(abs(p.x - q.x), abs(p.y - q.y))
        return listOf(
            distance(a.topLeft, b.topLeft),
            distance(a.topRight, b.topRight),
            distance(a.bottomRight, b.bottomRight),
            distance(a.bottomLeft, b.bottomLeft)
        ).maxOrNull() ?: 0f
    }
}
