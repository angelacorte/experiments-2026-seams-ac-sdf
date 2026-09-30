package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.expand
import it.unibo.collektive.sdf.or
import it.unibo.collektive.sdf.primitive.Arc
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.primitive.Segment
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of a shape composed of an arc, a segment,
 * and a circular dot, resembling a question mark.
 *
 * @param center The (X, Y) coordinates of the arc's center.
 * @param radius The radius of the arc, which also dictates the size and position of the other components.
 * @property thickness The thickness of the shape (default is 0.0).
 */
class QuestionMark(center: Position, radius: Double, private val thickness: Double = 0.0) : SDF {
    init {
        require(radius > 0.0) { "Question mark radius must be positive, got $radius" }
        require(thickness >= 0.0) { "Question mark thickness cannot be negative, got $thickness" }
    }

    private val arc = Arc(center, radius, -PI / 2, 3 * PI / 2)

    private val segment = Segment(Position(center.x, center.y - radius), Position(center.x, center.y - 2 * radius))

    private val dot = Circle(Position(center.x, center.y - 3 * radius), radius * 0.12)

    override fun invoke(position: Position): Double = ((arc or segment or dot) expand thickness)(position)
}
