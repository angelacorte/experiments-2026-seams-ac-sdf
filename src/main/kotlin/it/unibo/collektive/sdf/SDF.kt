package it.unibo.collektive.sdf

import it.unibo.collektive.model.Position
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

    fun isInside(position: Position): Boolean = this(position) <= 0.0
}

/**
 * Creates a new Signed Distance Field (SDF) representing the inverse of the given [shape].
 * * By negating the distance value, the internal regions (traditionally negative) become
 * external (positive), and the external regions become internal.
 */
fun inverseSDF(shape: SDF): SDF = SDF { position -> -shape(position) }

/** Union, `min(φA, φB)`: inside [this] or inside [other]. */
infix fun SDF.or(other: SDF): SDF = SDF { position -> minOf(this(position), other(position)) }

/** Intersection, `max(φA, φB)`: inside [this] and inside [other]. */
infix fun SDF.and(other: SDF): SDF = SDF { position -> maxOf(this(position), other(position)) }

/** Union of all these shapes. */
fun Iterable<SDF>.union(): SDF = reduce { union, shape -> union or shape }

/** Complement, `-φ`: outside [this]. */
operator fun SDF.not(): SDF = inverseSDF(this)

/** Difference, `max(φA, -φB)`: inside [this] but outside [other]. */
operator fun SDF.minus(other: SDF): SDF = this and !other

/** [this] grown outward by [distance], rounding its corners: `φ - distance`. A negative [distance] shrinks it. */
infix fun SDF.expand(distance: Double): SDF = SDF { position -> this(position) - distance }

/** A band of half-width [thickness] along the boundary of [this]: `|φ| - thickness`. */
infix fun SDF.ring(thickness: Double): SDF = SDF { position -> abs(this(position)) - thickness }

/** [this] moved by ([dx], [dy]). Rigid, so distances stay exact. */
fun SDF.translate(dx: Double, dy: Double): SDF = SDF { position -> this(Position(position.x - dx, position.y - dy)) }

/** [this] rotated by [angle] (radians, counterclockwise) around [pivot]. Rigid, so distances stay exact. */
fun SDF.rotate(angle: Double, pivot: Position = Position(0.0, 0.0)): SDF = SDF { position ->
    // Sample the original shape at the query point rotated back by -angle.
    val dx = position.x - pivot.x
    val dy = position.y - pivot.y
    this(Position(pivot.x + dx * cos(angle) + dy * sin(angle), pivot.y - dx * sin(angle) + dy * cos(angle)))
}

/** [this] uniformly scaled by [factor] around [pivot]; distances are rescaled too, so they stay exact. */
fun SDF.scale(factor: Double, pivot: Position = Position(0.0, 0.0)): SDF {
    require(factor > 0.0) { "Scale factor must be positive, got $factor" }
    return SDF { position ->
        this(Position(pivot.x + (position.x - pivot.x) / factor, pivot.y + (position.y - pivot.y) / factor)) * factor
    }
}
