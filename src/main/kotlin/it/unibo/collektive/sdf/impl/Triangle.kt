package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of a triangle, with vertices given in any order.
 *
 * @property a The first vertex.
 * @property b The second vertex.
 * @property c The third vertex.
 */
class Triangle(private val a: Position, private val b: Position, private val c: Position) : SDF {
    private val edges = listOf(Segment(a, b), Segment(b, c), Segment(c, a))

    override fun invoke(position: Position): Double {
        val distance = edges.minOf { it(position) }
        val sides = listOf(side(a, b, position), side(b, c, position), side(c, a, position))
        val inside = sides.all { it >= 0 } || sides.all { it <= 0 }
        return if (inside) -distance else distance
    }

    /** Positive when [position] lies on the left of the line going [from] → [to]. */
    private fun side(from: Position, to: Position, position: Position) =
        (to.x - from.x) * (position.y - from.y) - (to.y - from.y) * (position.x - from.x)
}
