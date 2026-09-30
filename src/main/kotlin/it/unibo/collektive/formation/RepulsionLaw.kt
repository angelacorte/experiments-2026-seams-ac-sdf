package it.unibo.collektive.formation

import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import kotlin.math.pow

/** How a neighbor pushes a device away, given the lattice spacing. */
sealed interface RepulsionLaw {
    /** The push on the device of a neighbor at [offset] (`neighbor - self`), for the lattice [spacing]. */
    fun force(offset: Vector2D, spacing: Double): SpeedControl2D

    /** Linear in the overlap, and zero beyond the spacing (see [softRepulsionForce]). */
    data class SoftDisk(val stiffness: Double) : RepulsionLaw {
        override fun force(offset: Vector2D, spacing: Double) = softRepulsionForce(offset, stiffness, spacing)
    }

    /** Inverse square of the distance, never zero (see [repulsionForce]). */
    data class InverseSquare(val coefficient: Double) : RepulsionLaw {
        override fun force(offset: Vector2D, spacing: Double) = repulsionForce(offset, coefficient, spacing)
    }
}

/**
 * Soft-disk repulsion from a neighbor at [relativePosition] (`neighbor - self`): it points away from the neighbor,
 * is linear in the overlap `spacing - distance` (scaled by [coefficient]) and vanishes beyond [spacing].
 */
fun softRepulsionForce(relativePosition: Vector2D, coefficient: Double, spacing: Double): SpeedControl2D {
    val distance = relativePosition.norm
    if (distance == 0.0 || distance >= spacing) return zeroSpeed
    return relativePosition * (-coefficient * (spacing - distance) / distance)
}

/**
 * The repulsive force exerted by a neighbor located at [relativePosition] (i.e., `neighbor - self`).
 * It points away from the neighbor, with the inverse-square magnitude `coefficient * desiredDistance^3 / distance^2`
 * (so `coefficient * desiredDistance` at the [desiredDistance]).
 */
fun repulsionForce(relativePosition: Vector2D, coefficient: Double, desiredDistance: Double): SpeedControl2D {
    val distance = relativePosition.norm
    if (distance == 0.0) return zeroSpeed
    val direction: SpeedControl2D = relativePosition * (1.0 / distance)
    val repulsionCoefficient: Double = coefficient * desiredDistance.pow(3)
    val repulsionForce: Double = repulsionCoefficient / distance.pow(2)
    return direction * (-repulsionForce)
}
