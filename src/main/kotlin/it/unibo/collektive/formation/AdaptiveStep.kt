package it.unibo.collektive.formation

import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.dot
import it.unibo.collektive.geometry.zeroSpeed
import kotlin.math.max
import kotlin.math.min

/**
 * How the step size of a device adapts to overshoots (see [AdaptiveStep]).
 *
 * @property decrease the factor applied to the gain when the control reverses.
 * @property increase the factor applied to the gain while the control keeps its direction.
 * @property minGain the smallest gain, so that a device can always start moving again.
 */
open class StepRule( // Open: Alchemist finds a final class twice, and cannot build it by `type`
    val decrease: Double,
    val increase: Double,
    val minGain: Double,
)

/**
 * A step size that adapts to overshoots, as in Rprop:
 * devices move fast while spreading, and settle (withoutflickering) once in place.
 *
 * @property gain the scale of the velocity, up to [FULL].
 * @property lastControl the control of the previous round.
 */
data class AdaptiveStep(val gain: Double = FULL, val lastControl: Vector2D = zeroSpeed) {
    /**
     * The step for the new [control]: the gain shrinks when the control reverses (the device overshot), and grows back
     * while it keeps its direction, as set by the [rule].
     */
    fun adapt(control: Vector2D, rule: StepRule): AdaptiveStep {
        val reversed = lastControl dot control < 0.0
        val adapted = if (reversed) max(rule.minGain, gain * rule.decrease) else min(FULL, gain * rule.increase)
        return AdaptiveStep(adapted, control)
    }

    /** Constants of [AdaptiveStep]. */
    companion object {
        /** The gain of a full step, at the velocity of the control. */
        const val FULL = 1.0
    }
}
