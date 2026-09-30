package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
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
    private val vertices = vertices.toList()

    init {
        require(this.vertices.size >= 3) { "A polygon needs at least 3 vertices, got ${this.vertices.size}" }
    }

    override fun invoke(position: Position): Double {
        var squaredDistance = squaredLength(position.x - vertices[0].x, position.y - vertices[0].y)
        var sign = 1.0
        var previous = vertices.last()
        for (current in vertices) {
            val edgeX = previous.x - current.x
            val edgeY = previous.y - current.y
            val toPointX = position.x - current.x
            val toPointY = position.y - current.y
            val along = ((toPointX * edgeX + toPointY * edgeY) / squaredLength(edgeX, edgeY)).coerceIn(0.0, 1.0)
            squaredDistance = min(
                squaredDistance,
                squaredLength(toPointX - edgeX * along, toPointY - edgeY * along),
            )
            val isAboveStart = position.y >= current.y
            val isBelowEnd = position.y < previous.y
            val isLeftOfEdge = edgeX * toPointY > edgeY * toPointX
            // The edge is crossed by the ray going towards +x exactly when all three hold, or none does.
            if ((isAboveStart && isBelowEnd && isLeftOfEdge) || (!isAboveStart && !isBelowEnd && !isLeftOfEdge)) {
                sign = -sign
            }
            previous = current
        }
        return sign * sqrt(squaredDistance)
    }

    private fun squaredLength(x: Double, y: Double) = x * x + y * y
}
