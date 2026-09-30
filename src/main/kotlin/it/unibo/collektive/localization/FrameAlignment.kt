package it.unibo.collektive.localization

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.minus
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Learns the orthogonal map T (a rotation or a reflection) from the body frame to the anchor frame, fitting the
 * correlation `C = Σ Δp·mᵀ` between the change Δp of the estimated position and the motion m in the body frame
 * (Δp ≈ T·m), as in 2D Procrustes (see also Cornejo & Nagpal, WAFR 2014).
 *
 * @property correlationXX the entry `Σ Δp.x·m.x` of C.
 * @property correlationXY the entry `Σ Δp.x·m.y` of C.
 * @property correlationYX the entry `Σ Δp.y·m.x` of C.
 * @property correlationYY the entry `Σ Δp.y·m.y` of C.
 * @property motionEnergy the energy of the motion in the body frame, `Σ|m|²`.
 * @property lastPosition the previous estimated position, null if unknown.
 * @property lastTravelled the previous [learn]ed displacement in the body frame, null before the first one.
 */
data class FrameAlignment(
    val correlationXX: Double = 0.0,
    val correlationXY: Double = 0.0,
    val correlationYX: Double = 0.0,
    val correlationYY: Double = 0.0,
    val motionEnergy: Double = 0.0,
    val lastPosition: Position? = null,
    val lastTravelled: Vector2D? = null,
) {
    private val rotationFit get() = hypot(correlationXX + correlationYY, correlationYX - correlationXY)
    private val reflectionFit get() = hypot(correlationXX - correlationYY, correlationXY + correlationYX)

    /** 1 when the moves span the plane, 0 when they are collinear (rotation and reflection fit equally well). */
    val confidence get() = if (motionEnergy > 0.0) abs(rotationFit - reflectionFit) / motionEnergy else 0.0

    /**
     * Whether the node moved enough (a [motionEnergy] above [minMotionEnergy]) and in enough directions
     * (a [confidence] above [minConfidence]) to steer with [toBody].
     */
    fun isConfident(minMotionEnergy: Double, minConfidence: Double): Boolean =
        motionEnergy > minMotionEnergy && confidence > minConfidence

    /**
     * Learns from the new estimated [position] (null if unknown) and the displacement [travelled] since the start in
     * the body frame, keeping [forgettingFactor] of the past evidence; changes of the estimate above [maxDisplacement]
     * are taken as frame jumps rather than moves, and ignored.
     */
    fun learn(
        position: Position?,
        travelled: Vector2D,
        forgettingFactor: Double,
        maxDisplacement: Double,
    ): FrameAlignment {
        val estimated = if (position != null && lastPosition != null) position - lastPosition else null
        val perceived = lastTravelled?.let { SpeedControl2D(travelled.x - it.x, travelled.y - it.y) }
        val learned = when {
            // No previous estimate, or a frame jump.
            estimated == null || perceived == null || estimated.norm > maxDisplacement -> this
            else -> copy(
                correlationXX = forgettingFactor * correlationXX + estimated.x * perceived.x,
                correlationXY = forgettingFactor * correlationXY + estimated.x * perceived.y,
                correlationYX = forgettingFactor * correlationYX + estimated.y * perceived.x,
                correlationYY = forgettingFactor * correlationYY + estimated.y * perceived.y,
                motionEnergy = forgettingFactor * motionEnergy + perceived.x.pow(2) + perceived.y.pow(2),
            )
        }
        return learned.copy(lastPosition = position, lastTravelled = travelled)
    }

    /** Maps [direction] from the anchor frame to the body frame: `Tᵀ·direction`. */
    fun toBody(direction: Vector2D): SpeedControl2D = if (rotationFit >= reflectionFit) {
        val cosine = (correlationXX + correlationYY) / rotationFit
        val sine = (correlationYX - correlationXY) / rotationFit
        SpeedControl2D(cosine * direction.x + sine * direction.y, -sine * direction.x + cosine * direction.y)
    } else {
        val cosine = (correlationXX - correlationYY) / reflectionFit
        val sine = (correlationXY + correlationYX) / reflectionFit
        SpeedControl2D(cosine * direction.x + sine * direction.y, sine * direction.x - cosine * direction.y)
    }
}
