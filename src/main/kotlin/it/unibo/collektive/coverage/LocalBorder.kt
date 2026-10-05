package it.unibo.collektive.coverage

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.gradientToSDF

private const val GRADIENT_STEP = 0.001 // Step of the central differences on the SDF, well below the lattice spacing

/**
 * The border of a shape near a device, taken as a straight line.
 *
 * @property distance the signed distance of the device from the border (the SDF: not positive inside the shape).
 * @property outward the unit normal of the border, pointing out of the shape (zero where it is undefined).
 */
data class LocalBorder(val distance: Double, val outward: SpeedControl2D) {
    /** Whether the device is inside the shape. */
    val isInside: Boolean get() = distance <= 0.0

    /** The unit direction towards the shape: zero inside it, or where the normal is undefined. */
    val towardsShape: SpeedControl2D get() = if (isInside) zeroSpeed else outward * -1.0

    /** The mirror image across this border of the point at [offset] from the device. */
    fun mirror(offset: Vector2D): SpeedControl2D {
        val offsetDistance = distance + offset.x * outward.x + offset.y * outward.y // The signed distance of that point
        return offset + outward * (-2.0 * offsetDistance)
    }

    /** Factory for [LocalBorder]. */
    companion object {
        /** The border of [shape] near [position], with the normal from central differences on the SDF. */
        fun of(shape: SDF, position: Position): LocalBorder {
            val gradient = gradientToSDF(shape, position, GRADIENT_STEP)
            val outward = if (gradient.norm > 0.0) gradient * (1.0 / gradient.norm) else zeroSpeed
            return LocalBorder(shape(position), outward)
        }
    }
}
