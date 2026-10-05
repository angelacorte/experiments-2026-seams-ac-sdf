package it.unibo.collektive.coverage

import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.dot
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.zeroSpeed

/**
 * The lattice around a device, as offsets `neighbor - self`.
 *
 * @property neighbors the offsets of the real neighbors.
 * @param border the border of the shape near the device.
 */
class LatticeNeighborhood(val neighbors: List<Vector2D>, border: LocalBorder) {
    private val mirrors: List<Vector2D> = when {
        border.isInside -> {
            val reach = neighbors.maxOfOrNull { it.norm } ?: 0.0
            neighbors.map(border::mirror).filter { it.norm <= reach }
        }
        else -> emptyList()
    }

    private val all = neighbors + mirrors
    private val walls = mirrors.toHashSet()

    /** Whether the device has no (real) neighbors. */
    val isEmpty: Boolean get() = neighbors.isEmpty()

    /** The [RING_SIZE] nearest neighbors, real or mirrored: the first ring of the lattice. */
    val ring: List<Vector2D> = all.sortedBy { it.norm }.take(RING_SIZE)

    /** The mean distance of the [ring] (NaN if [isEmpty]). */
    val ringRadius: Double = ring.map { it.norm }.average()

    /** The neighbors, real or mirrored, within [RepulsionLaw.Spring.REACH] of the lattice [spacing]. */
    fun near(spacing: Double): List<Vector2D> = all.filter { it.norm < RepulsionLaw.Spring.REACH * spacing }

    /**
     * The squeeze `spacing - distance` of the neighbors [near] the device, summed and shared among the [RING_SIZE] of a
     * full ring: as the mean in a hexagonal lattice, but larger with fewer neighbors (e.g., in a chain), whose bonds
     * then get squeezed until they buckle into the empty room.
     */
    fun pressure(spacing: Double): Double = near(spacing).sumOf { spacing - it.norm } / RING_SIZE

    /**
     * The push on the device of the [pushers][RepulsionLaw.Pairwise.pushers] of the [law], for the lattice [spacing].
     * A mirror image is a wall: it pushes the device off the border, and never pulls it there (as the attraction of a
     * [RepulsionLaw.Spring] would, gluing the devices to the border).
     */
    fun repulsion(spacing: Double, law: RepulsionLaw.Pairwise): SpeedControl2D =
        law.pushers(this, spacing).fold(zeroSpeed) { total, offset ->
            val force = law.force(offset, spacing)
            if (offset in walls && (force dot offset) > 0.0) total else total + force
        }

    /** Constants of [LatticeNeighborhood]. */
    companion object {
        /** The neighbors in the first ring of a hexagonal lattice. */
        const val RING_SIZE = 6
    }
}
