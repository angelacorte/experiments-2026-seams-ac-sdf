package it.unibo.collektive.model

import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A two-dimensional position in the simulation coordinate system.
 *
 * @property x The horizontal coordinate.
 * @property y The vertical coordinate.
 */
data class Position(override val x: Double, override val y: Double) : Vector2D

/** Adds the components of [position] to this position. */
// operator fun Position.plus(position: Position): Position = Position(x + position.x, y + position.y)

operator fun Position.plus(other: Vector2D): Position = Position(x + other.x, y + other.y)

/** Subtracts the components of [position] from this position. */
operator fun Position.minus(position: Position): Position = Position(x - position.x, y - position.y)

/** Divides the coordinates of this position by the corresponding coordinates of [position]. */
operator fun Position.div(position: Position): Position = Position(x / position.x, y / position.y)

/** Converts this position to a speed control by dividing its coordinates by [scalar]. */
operator fun Position.div(scalar: Double): SpeedControl2D = SpeedControl2D(x / scalar, y / scalar)

/**
 * Calculate the Euclidean distance between two points in 2D space.
 */
fun Position.euclideanDistanceTo(pos: Position): Double = sqrt((this.x - pos.x).pow(2) + (this.y - pos.y).pow(2))
