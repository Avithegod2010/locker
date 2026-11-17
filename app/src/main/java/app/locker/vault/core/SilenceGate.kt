package app.locker.vault.core

/**
 * Automatic silence skipping for voice memos.
 *
 * The recorder is paused after a sustained quiet stretch and resumed as soon as the speaker
 * starts again. Two thresholds are used on purpose: pausing at [pauseBelow] and resuming only
 * above [resumeAbove] stops the gate from chattering on breaths and room noise.
 *
 * Pure by design so the timing rules are unit tested instead of tuned by ear on a device.
 */
data class SilenceGateState(
    val autoPaused: Boolean = false,
    /** -1 means "no quiet streak yet"; zero is a valid clock value. */
    val quietSince: Long = NO_STREAK
) {
    companion object {
        const val NO_STREAK = -1L
    }
}

enum class SilenceAction { NONE, PAUSE, RESUME }

data class SilenceDecision(val state: SilenceGateState, val action: SilenceAction)

object SilenceGate {
    const val DEFAULT_PAUSE_BELOW = 0.02f
    const val DEFAULT_RESUME_ABOVE = 0.045f
    const val DEFAULT_HOLD_MS = 1_200L

    /**
     * Feeds one amplitude sample (0..1). [now] is monotonic milliseconds; the caller owns the
     * clock so the behaviour can be tested deterministically.
     */
    fun update(
        state: SilenceGateState,
        amplitude: Float,
        now: Long,
        pauseBelow: Float = DEFAULT_PAUSE_BELOW,
        resumeAbove: Float = DEFAULT_RESUME_ABOVE,
        holdMs: Long = DEFAULT_HOLD_MS
    ): SilenceDecision {
        val level = amplitude.coerceIn(0f, 1f)
        val hold = holdMs.coerceAtLeast(0L)

        if (state.autoPaused) {
            return if (level >= resumeAbove) {
                SilenceDecision(SilenceGateState(autoPaused = false), SilenceAction.RESUME)
            } else {
                SilenceDecision(state, SilenceAction.NONE)
            }
        }

        if (level >= pauseBelow) {
            // Sound (or loud enough room tone): forget any quiet streak.
            return if (state.quietSince < 0L) {
                SilenceDecision(state, SilenceAction.NONE)
            } else {
                SilenceDecision(
                    SilenceGateState(autoPaused = false, quietSince = SilenceGateState.NO_STREAK),
                    SilenceAction.NONE
                )
            }
        }

        val quietSince = if (state.quietSince < 0L) now else state.quietSince
        return if (now - quietSince >= hold) {
            SilenceDecision(
                SilenceGateState(autoPaused = true, quietSince = SilenceGateState.NO_STREAK),
                SilenceAction.PAUSE
            )
        } else {
            SilenceDecision(
                SilenceGateState(autoPaused = false, quietSince = quietSince),
                SilenceAction.NONE
            )
        }
    }

    /** Manual pause/resume by the user must not leave the gate thinking it owns the recorder. */
    fun reset(): SilenceGateState = SilenceGateState()
}
