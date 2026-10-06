package it.unibo.collektive.sdf

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.times
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

/** The area of [this] within the square from ([from], [from]) to ([to], [to]), counting the cells of side [step]. */
fun SDF.area(from: Double, to: Double, step: Double): Double {
    val cells = ((to - from) / step).toInt()
    val inside = (0 until cells).sumOf { i ->
        (0 until cells).count { j -> isInside(Position(from + (i + 0.5) * step, from + (j + 0.5) * step)) }
    }
    return inside * step * step
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

/**
 * A point deep inside [this], found from [start] following the [gradientToSDF] (of step [epsilon]), without sampling:
 * down to the border (sphere tracing), if [start] is outside, then inward while the distance from the border grows,
 * halving the step until below [epsilon]. It is a local maximum of the depth (a point of the medial axis), so it
 * lies inside the shape, but it is the deepest one only for convex shapes. At most [maxSteps] moves per phase.
 */
fun SDF.deepestPointFrom(start: Position, epsilon: Double = 1e-3, maxSteps: Int = 10_000): Position {
    // Where the gradient vanishes (e.g., at the center of a ring), any direction: the +x axis.
    fun Position.inward(length: Double): Position = gradientToSDF(this@deepestPointFrom, this, epsilon)
        .let { if (it.norm > 0.0) it * (1 / it.norm) else SpeedControl2D(-1.0, 0.0) }
        .let { Position(x - it.x * length, y - it.y * length) }
    // Sphere tracing: the border is at least this(point) away, towards the gradient.
    var point = start
    var moves = 0
    while (this(point) > 0.0 && moves++ < maxSteps) {
        point = point.inward(this(point) + epsilon)
    }
    // Gradient ascent of the depth, with a step that halves when it stops growing.
    var step = maxOf(-this(point), 1.0)
    moves = 0
    while (step >= epsilon && moves++ < maxSteps) {
        val next = point.inward(step)
        if (this(next) < this(point)) point = next else step /= 2
    }
    return point
}
