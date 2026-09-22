package it.unibo.common

/**
 * 2D [dimension] point-like contract exposing coordinates and
 * a self-referencing [position] for DSLs that expect a `([x], [y])` pair.
 */
interface Vector2D {
    val x: Double
    val y: Double

    val dimension: Int
        get() = 2
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
fun zeroSpeed(): SpeedControl2D = SpeedControl2D(0.0, 0.0)

operator fun Vector2D.times(scalar: Double): Vector2D = SpeedControl2D(x * scalar, y * scalar)

operator fun SpeedControl2D.plus(other: Vector2D): SpeedControl2D = SpeedControl2D(x + other.x, y + other.y)
