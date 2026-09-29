package it.unibo.collektive.sdf.shape

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.euclideanDistanceTo
import it.unibo.collektive.model.polar
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Segment
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular star, with one point facing up (+y).
 *
 * A star with [pointCount] points is made of 2 · [pointCount] mirrored slices of angle π / [pointCount],
 * and within each slice its boundary is a single straight edge, from a tip to the next inner vertex.
 * So, as in Inigo Quilez's `sdStar`, the query point is folded into one slice and measured against that edge only.
 * For a hollow or rounded star, use [it.unibo.collektive.sdf.ring] or [it.unibo.collektive.sdf.expand].
 *
 * @param center The (X, Y) coordinates of the star's center.
 * @param radius The outer radius, from the center to each tip.
 * @param pointCount The number of points of the star.
 * @param spikiness How sharp the points are, between 2 and [pointCount] (Quilez's `m`): 2 gives a regular polygon,
 * higher values carve deeper notches, and [pointCount] shrinks the points to lines.
 */
class Star(
    private val center: Position,
    radius: Double,
    pointCount: Int,
    spikiness: Double = pointCount / 2.0,
) : SDF {
    init {
        require(radius > 0.0) { "Star radius must be positive, got $radius" }
        require(pointCount >= 2) { "A star needs at least two points, got $pointCount" }
        require(spikiness in 2.0..pointCount.toDouble()) {
            "Star spikiness must be between 2 and $pointCount, got $spikiness"
        }
    }

    /** The angle between a tip and the next inner vertex. */
    private val slice = PI / pointCount

    /** The tip of the folded slice, on the +x axis. */
    private val tip = Position(radius, 0.0)

    /** The inner vertex of the folded slice, where the edges leaving two adjacent tips meet. */
    private val notch = ORIGIN.polar(radius * sin(PI / spikiness - slice) / sin(PI / spikiness), slice)

    private val edge = Segment(tip, notch)

    override fun invoke(position: Position): Double {
        val folded = fold(position)
        val distance = edge(folded)
        return if (isOnCenterSide(folded)) -distance else distance
    }

    /**
     * Rotates and mirrors [position] into the slice between the tip (angle 0) and the notch (angle [slice]),
     * keeping its distance from the center: by symmetry, the star's SDF is the same there.
     */
    private fun fold(position: Position): Position {
        // Clockwise from +y, so that the angle 0 is the upper tip.
        val angle = atan2(position.x - center.x, position.y - center.y).mod(2 * slice)
        return ORIGIN.polar(position.euclideanDistanceTo(center), minOf(angle, 2 * slice - angle))
    }

    /** Whether [position], inside the slice, lies on the same side of the edge as the center, i.e., inside the star. */
    private fun isOnCenterSide(position: Position): Boolean =
        (notch.x - tip.x) * (position.y - tip.y) - (notch.y - tip.y) * (position.x - tip.x) > 0

    private companion object {
        val ORIGIN = Position(0.0, 0.0)
    }
}
