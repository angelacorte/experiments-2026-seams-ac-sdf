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
        val abX = end.x - start.x
        val abY = end.y - start.y
        val apX = position.x - start.x
        val apY = position.y - start.y

        val abLenSq = abX * abX + abY * abY

        if (abLenSq == 0.0) return position.euclideanDistanceTo(start)

        val t = (apX * abX + apY * abY) / abLenSq

        val tClamped = t.coerceIn(0.0, 1.0)

        val closestX = start.x + tClamped * abX
        val closestY = start.y + tClamped * abY

        return position.euclideanDistanceTo(Position(closestX, closestY)) - thickness
    }
}
