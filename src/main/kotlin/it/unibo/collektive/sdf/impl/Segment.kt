package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.euclideanDistanceTo
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of a line segment.
 *
 * @property start The (X, Y) coordinates of the starting point of the segment.
 * @property end The (X, Y) coordinates of the ending point of the segment.
 * @property thickness The thickness of the segment (default is 0.0).
 */
class Segment(private val start: Position, private val end: Position, private val thickness: Double = 0.0) : SDF {
    override fun invoke(position: Position): Double {
        val segmentX = end.x - start.x
        val segmentY = end.y - start.y
        val pointX = position.x - start.x
        val pointY = position.y - start.y
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY

        if (segmentLengthSquared == 0.0) return position.euclideanDistanceTo(start)

        val projectionFactor = (pointX * segmentX + pointY * segmentY) / segmentLengthSquared
        val clampedProjectionFactor = projectionFactor.coerceIn(0.0, 1.0)
        val closestX = start.x + clampedProjectionFactor * segmentX
        val closestY = start.y + clampedProjectionFactor * segmentY

        return position.euclideanDistanceTo(Position(closestX, closestY)) - thickness
    }
}
