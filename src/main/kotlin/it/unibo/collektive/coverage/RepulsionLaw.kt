package it.unibo.collektive.coverage

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.times
import it.unibo.collektive.sdf.SDF

/** How the neighbors move a device apart. */
sealed interface RepulsionLaw {
    /** The control of a device at [position] in [shape], with its neighbors at [offsets] (`neighbor - self`). */
    fun control(offsets: List<Vector2D>, shape: SDF, position: Position): SpeedControl2D

    /**
     * Lloyd's rule, as in the coverage control of Cortés et al.: the device moves towards the centroid of its Voronoi
     * cell within [reach], clipped to the shape (see [voronoiCentroid]), so that every device ends up with an equal
     * share of the shape, in a hexagonal lattice away from the border.
     *
     * @property gain the fraction of the way to the centroid covered at each round: 1, the step of Lloyd's algorithm,
     * with a fixed step size (a [StepRule] that never changes the gain), since the adaptive one throttles the devices
     * in a crowd, where the centroid keeps changing side, and they then take long to spread a pile.
     * @property reach the largest distance of a point of the cell: half the communication range, so that the
     * neighbors that shape the cell are all in range.
     */
    data class Lloyd(val gain: Double, val reach: Double) : RepulsionLaw {
        override fun control(offsets: List<Vector2D>, shape: SDF, position: Position) =
            voronoiCentroid(shape, position, offsets, reach) * gain
    }
}
