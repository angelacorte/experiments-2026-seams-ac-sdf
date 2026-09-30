package it.unibo.collektive.geometry

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A point in the plane, in the simulation coordinate system.
 *
 * @property x The horizontal coordinate.
 * @property y The vertical coordinate.
 */
data class Position(override val x: Double, override val y: Double) : Vector2D {
    /** Well-known positions. */
    companion object {
        /** The origin of the coordinate system. */
        val origin = Position(0.0, 0.0)
    }
}

/** This position moved by [displacement]. */
operator fun Position.plus(displacement: Vector2D): Position = Position(x + displacement.x, y + displacement.y)

/** The displacement from [other] to this position. */
operator fun Position.minus(other: Position): SpeedControl2D = SpeedControl2D(x - other.x, y - other.y)

/** The Euclidean distance between this position and [other]. */
fun Position.euclideanDistanceTo(other: Position): Double = hypot(x - other.x, y - other.y)

/** The point at [radius] from [this@polar], along [angle] (radians, counterclockwise from +x). */
fun Position.polar(radius: Double, angle: Double): Position = Position(x + radius * cos(angle), y + radius * sin(angle))
