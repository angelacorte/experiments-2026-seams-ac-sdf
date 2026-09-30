package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.dot
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of a line segment.
 *
 * @property start The (X, Y) coordinates of the starting point of the segment.
 * @param end The (X, Y) coordinates of the ending point of the segment.
 */
class Segment(private val start: Position, end: Position) : SDF {
    private val direction = end - start
    private val squaredLength = direction dot direction

    override fun invoke(position: Position): Double {
        val toPoint = position - start
        if (squaredLength == 0.0) return toPoint.norm
        // How far along the segment the closest point lies: 0 at the start, 1 at the end.
        val along = ((toPoint dot direction) / squaredLength).coerceIn(0.0, 1.0)
        return (toPoint - direction * along).norm
    }
}
