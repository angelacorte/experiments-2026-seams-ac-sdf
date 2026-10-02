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

    /** The neighbors pushing the device, for the lattice [spacing]: the [ring][LatticeNeighborhood.ring]. */
    fun pushers(neighborhood: LatticeNeighborhood, spacing: Double): List<Vector2D> = neighborhood.ring

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
     * Attraction and repulsion, as in the physicomimetics of Spears et al.: linear in `distance - spacing` (pushing
     * within the spacing, pulling beyond it) and zero beyond [REACH] spacings, so that only the first ring of a
     * hexagonal lattice counts.
     * The pull is the [attraction] fraction of the push: with a full one, a uniform crowd is in balance (push and pull
     * cancel out up to [REACH]) and never spreads; below it, the border of a crowd is pushed out.
     */
    data class Spring(val stiffness: Double, val attraction: Double) : RepulsionLaw {
        override fun force(offset: Vector2D, spacing: Double): SpeedControl2D {
            val distance = offset.norm
            if (distance == 0.0 || distance >= REACH * spacing) return zeroSpeed
            val gain = if (distance > spacing) stiffness * attraction else stiffness
            return offset * (gain * (distance - spacing) / distance)
        }

        /** All the neighbors within its [REACH], not only the ring. */
        override fun pushers(neighborhood: LatticeNeighborhood, spacing: Double) = neighborhood.near(spacing)

        /** Constants of [Spring]. */
        companion object {
            /** The reach of a neighbor, in spacings: below the second ring of a hexagonal lattice (`√3` spacings). */
            const val REACH = 1.5
        }
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
