package it.unibo.collektive.formation

import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import kotlin.math.pow

/** How a neighbor pushes a device away (or, for [Spring], also pulls it closer), given the lattice spacing. */
sealed interface RepulsionLaw {
    /** The force on the device of a neighbor at [offset] (`neighbor - self`), for the lattice [spacing]. */
    fun force(offset: Vector2D, spacing: Double): SpeedControl2D

    /**
     * Linear in the overlap, and zero beyond the spacing (see [softRepulsionForce]).
     *
     * @property stiffness how strongly the push grows with the overlap `spacing - distance`.
     */
    data class SoftDisk(val stiffness: Double) : RepulsionLaw {
        override fun force(offset: Vector2D, spacing: Double) = softRepulsionForce(offset, stiffness, spacing)
    }

    /**
     * Inverse square of the distance, never zero (see [repulsionForce]).
     *
     * @property coefficient the scale of the push: it is `coefficient * spacing` at the lattice spacing.
     */
    data class InverseSquare(val coefficient: Double) : RepulsionLaw {
        override fun force(offset: Vector2D, spacing: Double) = repulsionForce(offset, coefficient, spacing)
    }

    /**
     * Boids-like: a spring pulls the device towards neighbors beyond the spacing (cohesion), and a push keeps it
     * from getting closer than the spacing (separation); see [attractionRepulsionForce].
     *
     * @property stiffness the slope of the force around the spacing: the pull per unit of stretch of the spring.
     */
    data class Spring(val stiffness: Double) : RepulsionLaw {
        override fun force(offset: Vector2D, spacing: Double) = attractionRepulsionForce(offset, stiffness, spacing)
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

/**
 * The Boids-like attraction-repulsion force exerted by a neighbor located at [relativePosition]
 * (i.e., `neighbor - self`), with `d = |relativePosition|` and `d0 = desiredDistance`:
 *
 * - **cohesion (spring)**, when `d > d0`: a Hooke spring with rest length `d0` pulls toward the neighbor,
 *   with magnitude `attractionCoefficient * (d - d0)`;
 * - **separation**, when `d < d0`: the neighbor violates the minimum desired distance and pushes away,
 *   with magnitude `attractionCoefficient * d0 * (d0 / d - 1)`, which grows unbounded as `d -> 0`;
 * - **equilibrium**, when `d == d0`: no force.
 *
 * Both terms vanish at `d0` and have the same slope there (`attractionCoefficient`), so the force is continuous
 * and smooth around the desired distance.
 */
fun attractionRepulsionForce(
    relativePosition: Vector2D,
    attractionCoefficient: Double,
    desiredDistance: Double,
): SpeedControl2D {
    val distance = relativePosition.norm
    if (distance == 0.0) return zeroSpeed
    val direction: SpeedControl2D = relativePosition * (1.0 / distance)
    val netForce: Double = when {
        // Cohesion: spring with rest length desiredDistance, pulling toward the neighbor
        distance > desiredDistance -> attractionCoefficient * (distance - desiredDistance)
        // Separation: minimum distance violated, push away (negative = away from the neighbor)
        distance < desiredDistance -> -attractionCoefficient * desiredDistance * (desiredDistance / distance - 1.0)
        else -> 0.0
    }
    return direction * netForce
}
