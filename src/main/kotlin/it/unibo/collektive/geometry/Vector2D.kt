package it.unibo.collektive.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.apache.commons.math3.random.RandomGenerator

/** A vector in the plane: a displacement, a direction, or a velocity. */
interface Vector2D {
    /** The horizontal component. */
    val x: Double

    /** The vertical component. */
    val y: Double

    /** The Euclidean length of this vector. */
    val norm: Double get() = sqrt(x * x + y * y)
}

/** A velocity in the plane: the control input of a device. */
data class SpeedControl2D(override val x: Double, override val y: Double) : Vector2D {
    override fun toString(): String = "Control($x, $y)"
}

/** The null velocity. */
val zeroSpeed: SpeedControl2D = SpeedControl2D(0.0, 0.0)

/** The sum of this vector and [other]. */
operator fun Vector2D.plus(other: Vector2D): SpeedControl2D = SpeedControl2D(x + other.x, y + other.y)

/** The difference between this vector and [other]. */
operator fun Vector2D.minus(other: Vector2D): SpeedControl2D = SpeedControl2D(x - other.x, y - other.y)

/** This vector scaled by [scalar]. */
operator fun Vector2D.times(scalar: Double): SpeedControl2D = SpeedControl2D(x * scalar, y * scalar)

/** The dot product of this vector and [other]. */
infix fun Vector2D.dot(other: Vector2D): Double = x * other.x + y * other.y

/**
 * The (z component of the) cross product of this vector and [other]: positive when [other] points to the left of
 * this vector (counterclockwise from it), negative when it points to the right, and zero when they are parallel.
 */
infix fun Vector2D.cross(other: Vector2D): Double = x * other.y - y * other.x

/** This vector, scaled down to [maxNorm] if it is longer. */
fun Vector2D.limitedTo(maxNorm: Double): SpeedControl2D = when {
    norm > maxNorm -> this * (maxNorm / norm)
    else -> SpeedControl2D(x, y)
}

/** A unit velocity pointing in a random direction. */
fun RandomGenerator.randomDirection(): SpeedControl2D = (2 * PI * nextDouble()).let { SpeedControl2D(cos(it), sin(it)) }
