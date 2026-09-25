package it.unibo.common

import kotlin.math.sqrt

/**
 * 2D [dimension] point-like contract exposing coordinates and
 * a self-referencing [position] for DSLs that expect a `([x], [y])` pair.
 */
interface Vector2D {
    val x: Double
    val y: Double

    val dimension: Int
        get() = 2

    operator fun plus(v: Vector2D): SpeedControl2D = SpeedControl2D(x + v.x, y + v.y)

    val norm get() = sqrt(x * x + y * y)
}

/**
 * Planar control input.
 */
data class SpeedControl2D(override val x: Double, override val y: Double) : Vector2D {
    override fun toString(): String = "Control($x, $y)"
}

/**
 * Zero control input utility.
 */
val zeroSpeed: SpeedControl2D = SpeedControl2D(0.0, 0.0)

/** Scales this vector by [scalar]. */
operator fun Vector2D.times(scalar: Double) = SpeedControl2D(x * scalar, y * scalar)

/** Adds the coordinates of [other] to this control input. */
operator fun SpeedControl2D.plus(other: Vector2D): SpeedControl2D = SpeedControl2D(x + other.x, y + other.y)

operator fun Vector2D.times(other: Vector2D): SpeedControl2D = SpeedControl2D(x * other.x, y * other.y)
