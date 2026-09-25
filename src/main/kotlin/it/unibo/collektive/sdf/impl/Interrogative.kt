package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI
import kotlin.math.min

/**
 * Represents a 2D Signed Distance Field (SDF) of a shape composed of an arc, a segment,
 * and a circular dot, resembling a question mark.
 *
 * @param center The (X, Y) coordinates of the arc's center.
 * @param radius The radius of the arc, which also dictates the size and position of the other components.
 * @property thickness The thickness of the shape (default is 0.0).
 */
class Interrogative(
    center: Position,
    radius: Double,
    private val thickness: Double = 0.0,
) : SDF {
    private val arc =
        Arc(center, radius, -PI / 2.0, 3.0 / 2.0 * PI,)

    private val segment = Segment(Position(center.x, center.y - radius), Position(center.x, center.y - 2.0 * radius),)

    private val dot = Circle(Position(center.x, center.y - 3 * radius), radius * 0.12)

    override fun invoke(position: Position): Double = min(min(arc(position), segment(position)), dot(position)) - thickness
}
