package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.cross
import it.unibo.collektive.geometry.dot
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.sdf.SDF
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Represents a 2D Signed Distance Field (SDF) of a simple polygon, convex or not, with vertices in any order
 * (clockwise or counterclockwise), as in Inigo Quilez's `sdPolygon`.
 * The distance is the one to the closest edge, and the sign comes from counting how many edges a ray
 * from the query point crosses.
 *
 * @param vertices The vertices of the polygon, in order along its boundary. At least three.
 */
class Polygon(vertices: List<Position>) : SDF {
    init {
        require(vertices.size >= 3) { "A polygon needs at least 3 vertices, got ${vertices.size}" }
    }

    /** Each edge as the pair (previous vertex, current vertex), closing the boundary. */
    private val edges: List<Pair<Position, Position>> = (listOf(vertices.last()) + vertices).zipWithNext()

    override fun invoke(position: Position): Double {
        var squaredDistance = Double.POSITIVE_INFINITY
        var isInside = false
        for ((previous, current) in edges) {
            val edge = previous - current
            val toPoint = position - current
            val along = ((toPoint dot edge) / (edge dot edge)).coerceIn(0.0, 1.0)
            val offset = toPoint - edge * along
            squaredDistance = min(squaredDistance, offset dot offset)
            val isAboveStart = position.y >= current.y
            val isBelowEnd = position.y < previous.y
            val isLeftOfEdge = (edge cross toPoint) > 0.0
            // The edge is crossed by the ray going towards +x exactly when all three hold, or none does.
            if (isAboveStart == isBelowEnd && isBelowEnd == isLeftOfEdge) {
                isInside = !isInside
            }
        }
        val distance = sqrt(squaredDistance)
        return if (isInside) -distance else distance
    }
}
