package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.common.pointsDistance
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of a line segment.
 *
 * @property a The (X, Y) coordinates of the starting point of the segment.
 * @property b The (X, Y) coordinates of the ending point of the segment.
 * @property thickness The thickness of the segment (default is 0.0).
 */
class Segment(private val a: Position, private val b: Position, private val thickness: Double = 0.0) : SDF {
    override fun invoke(position: Position): Double {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val apX = position.x - a.x
        val apY = position.y - a.y

        val abLenSq = abX * abX + abY * abY

        if (abLenSq == 0.0) return pointsDistance(position, a)

        val t = (apX * abX + apY * abY) / abLenSq

        val tClamped = t.coerceIn(0.0, 1.0)

        val closestX = a.x + tClamped * abX
        val closestY = a.y + tClamped * abY

        return pointsDistance(position, Position(closestX, closestY)) - thickness
    }
}
