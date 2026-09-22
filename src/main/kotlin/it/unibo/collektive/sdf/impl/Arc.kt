package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.euclideanDistanceTo
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Represents a 2D Signed Distance Field (SDF) of an arc.
 *
 * @property center The (X, Y) coordinates of the arc's center.
 * @property radius The radius of the arc.
 * @property startAngle The starting angle of the arc in radians.
 * @property aperture The angular length of the arc in radians.
 * @property thickness The thickness of the arc (default is 0.0).
 */
class Arc(
    private val center: Position,
    private val radius: Double,
    private val startAngle: Double,
    private val aperture: Double,
    private val thickness: Double = 0.0,
) : SDF {
    private val endAngle = startAngle + aperture
    private val start = Position(center.x + radius * cos(startAngle), center.y + radius * sin(startAngle))
    private val end = Position(center.x + radius * cos(endAngle), center.y + radius * sin(endAngle))

    override fun invoke(position: Position): Double {
        val angle = atan2(position.y - center.y, position.x - center.x)
        val normalizedAngle = (angle - startAngle).mod(2.0 * PI)
        val distance = when {
            normalizedAngle <= aperture -> abs(radius - position.euclideanDistanceTo(center))
            else -> min(position.euclideanDistanceTo(start), position.euclideanDistanceTo(end))
        }
        return distance - thickness
    }
}
