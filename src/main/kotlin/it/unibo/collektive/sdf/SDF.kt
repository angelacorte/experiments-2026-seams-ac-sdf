package it.unibo.collektive.sdf

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Represents a 2D Signed Distance Field (SDF).
 * A functional interface that evaluates the signed distance from a given point to the boundary of a shape.
 */
fun interface SDF {
    /**
     * Returns the distance of [position] from the surface of the SDF.
     * if the value is negative, the point is inside the SDF.
     */
    operator fun invoke(position: Position): Double

    /** Whether [position] lies inside this field or on its boundary. */
    fun isInside(position: Position): Boolean = this(position) <= 0.0

    /** Whether [position] lies strictly outside this field. */
    fun isOutside(position: Position): Boolean = this(position) > 0.0
}

/** Union, `min(A, B)`: inside [this] or inside [other]. */
infix fun SDF.or(other: SDF): SDF = SDF { position -> minOf(this(position), other(position)) }

/** Intersection, `max(A, B)`: inside [this] and inside [other]. */
infix fun SDF.and(other: SDF): SDF = SDF { position -> maxOf(this(position), other(position)) }

/** Union of all these shapes. The collection must not be empty. */
fun Iterable<SDF>.union(): SDF = reduceOrNull { union, shape -> union or shape }
    ?: error("Cannot build the union of an empty collection")

/** Complement, `-A`: outside [this]. */
operator fun SDF.not(): SDF = SDF { position -> -this(position) }

/** Difference, `max(A, -B)`: inside [this] but outside [other]. */
operator fun SDF.minus(other: SDF): SDF = this and !other

/** [this] grown outward by [distance], rounding its corners: `A - distance`. A negative [distance] shrinks it. */
infix fun SDF.expand(distance: Double): SDF = SDF { position -> this(position) - distance }

/** A band of half-width [thickness] along the boundary of [this]: |A| - thickness. */
infix fun SDF.ring(thickness: Double): SDF {
    require(thickness >= 0.0) { "Ring thickness cannot be negative, got $thickness" }
    return SDF { position -> abs(this(position)) - thickness }
}

/** The boundary of [this] as a zero-width stroke, `|A|`: to be thickened with [expand]. */
fun SDF.outline(): SDF = this ring 0.0

/** [this] moved by ([dx], [dy]). Rigid, so distances stay exact. */
fun SDF.translate(dx: Double, dy: Double): SDF = SDF { position -> this(Position(position.x - dx, position.y - dy)) }

/** [this] rotated by [angle] (radians, counterclockwise) around [pivot]. Rigid, so distances stay exact. */
fun SDF.rotate(angle: Double, pivot: Position = Position.origin): SDF = SDF { position ->
    // Sample the original shape at the query point rotated back by -angle.
    val rotation = Position(position.x - pivot.x, position.y - pivot.y)
    this(
        Position(
            pivot.x + rotation.x * cos(angle) + rotation.y * sin(angle),
            pivot.y - rotation.x * sin(angle) + rotation.y * cos(angle),
        ),
    )
}

/** [this] uniformly scaled by [factor] around [pivot]; distances are rescaled too, so they stay exact. */
fun SDF.scale(factor: Double, pivot: Position = Position.origin): SDF {
    require(factor > 0.0) { "Scale factor must be positive, got $factor" }
    return SDF { position ->
        this(Position(pivot.x + (position.x - pivot.x) / factor, pivot.y + (position.y - pivot.y) / factor)) * factor
    }
}

/**
 * The gradient of [sdf] at [currentPosition], by central differences of step [epsilon] (not divided by the step: only
 * its direction is meaningful). It points away from the shape.
 */
fun gradientToSDF(sdf: SDF, currentPosition: Position, epsilon: Double): SpeedControl2D = SpeedControl2D(
    sdf(Position(currentPosition.x + epsilon, currentPosition.y)) -
        sdf(Position(currentPosition.x - epsilon, currentPosition.y)),
    sdf(Position(currentPosition.x, currentPosition.y + epsilon)) -
        sdf(Position(currentPosition.x, currentPosition.y - epsilon)),
)
