package it.unibo.collektive.formation

import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import it.unibo.common.zeroSpeed

/**
 * The lattice around a device, as offsets `neighbor - self`
 *
 * @param border the border of the shape near the device.
 * @param ringSize the nearest neighbors (real or mirrored) forming the first [ring] of the lattice.
 */
class LatticeNeighborhood(private val neighbors: List<Vector2D>, border: LocalBorder, ringSize: Int) {
    private val mirrors: List<Vector2D> = when {
        border.isInside -> {
            val reach = neighbors.maxOfOrNull { it.norm } ?: 0.0
            neighbors.map(border::mirror).filter { it.norm <= reach }
        }
        else -> emptyList()
    }

    /** Whether the device has no (real) neighbors. */
    val isEmpty: Boolean get() = neighbors.isEmpty()

    /** The nearest neighbors, real or mirrored: the first ring of the lattice (6 of them in a hexagonal one). */
    val ring: List<Vector2D> = (neighbors + mirrors).sortedBy { it.norm }.take(ringSize)

    /** The mean distance of the [ring] (NaN if [isEmpty]). */
    val ringRadius: Double = ring.map { it.norm }.average()

    /** The push of the [ring] on the device with the repulsion [law], for the lattice [spacing]. */
    fun repulsion(spacing: Double, law: RepulsionLaw): SpeedControl2D =
        ring.fold(zeroSpeed) { total, offset -> total + law.force(offset, spacing) }
}
