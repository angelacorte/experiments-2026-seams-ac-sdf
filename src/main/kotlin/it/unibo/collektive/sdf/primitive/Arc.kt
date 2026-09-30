package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.euclideanDistanceTo
import it.unibo.collektive.geometry.polar
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/**
 * Represents a 2D Signed Distance Field (SDF) of an arc.
 *
 * @property center The (X, Y) coordinates of the arc's center.
 * @property radius The radius of the arc.
 * @property startAngle The starting angle of the arc in radians.
 * @property aperture The angular length of the arc in radians.
 */
class Arc(
    private val center: Position,
    private val radius: Double,
    private val startAngle: Double,
    private val aperture: Double,
) : SDF {
    init {
        require(radius >= 0.0) { "Arc radius cannot be negative, got $radius" }
        require(aperture > 0.0 && aperture <= 2 * PI) {
            "Arc aperture must be in (0, 2π], got $aperture"
        }
    }

    private val start = center.polar(radius, startAngle)
    private val end = center.polar(radius, startAngle + aperture)

    override fun invoke(position: Position): Double {
        // The angle of position around the center, measured counterclockwise from the start of the arc.
        val angle = (atan2(position.y - center.y, position.x - center.x) - startAngle).mod(2 * PI)
        return when {
            angle <= aperture -> abs(radius - position.euclideanDistanceTo(center))
            else -> min(position.euclideanDistanceTo(start), position.euclideanDistanceTo(end))
        }
    }
}
