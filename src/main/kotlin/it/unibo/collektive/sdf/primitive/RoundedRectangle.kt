package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned rectangle with a different rounding
 * on each corner, as in Inigo Quilez's `sdRoundedBox`.
 * Each radius must not exceed half of the shorter side.
 *
 * @param center The (X, Y) coordinates of the rectangle's center.
 * @param width The horizontal side.
 * @param height The vertical side.
 * @param corners The radius of each corner.
 */
class RoundedRectangle(private val center: Position, width: Double, height: Double, private val corners: Corners) :
    SDF {
    /** A rectangle with the same [radius] on all the corners. */
    constructor(center: Position, width: Double, height: Double, radius: Double) :
        this(center, width, height, Corners.all(radius))

    /** A rectangle with the given radius on each corner. */
    constructor(
        center: Position,
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double,
    ) : this(center, width, height, Corners(topLeft, topRight, bottomRight, bottomLeft))

    init {
        require(width > 0.0 && height > 0.0) {
            "Rounded rectangle sides must be positive, got $width × $height"
        }
        val maximumRadius = min(width, height) / 2
        require(corners.radii.all { it in 0.0..maximumRadius }) {
            "Corner radii must be between 0 and $maximumRadius, got ${corners.radii}"
        }
    }

    private val halfWidth = width / 2
    private val halfHeight = height / 2

    override fun invoke(position: Position): Double {
        val x = position.x - center.x
        val y = position.y - center.y
        val radius = when {
            x > 0.0 && y > 0.0 -> corners.topRight
            x > 0.0 -> corners.bottomRight
            y > 0.0 -> corners.topLeft
            else -> corners.bottomLeft
        }
        // The rectangle shrunk by the radius, then grown back by it: this rounds the corner.
        val excessX = abs(x) - halfWidth + radius
        val excessY = abs(y) - halfHeight + radius
        return min(max(excessX, excessY), 0.0) + hypot(max(excessX, 0.0), max(excessY, 0.0)) - radius
    }
}

/**
 * The radii of the four corners of a [RoundedRectangle]: 0 is a sharp corner.
 *
 * @property topLeft The radius of the corner towards -x, +y.
 * @property topRight The radius of the corner towards +x, +y.
 * @property bottomRight The radius of the corner towards +x, -y.
 * @property bottomLeft The radius of the corner towards -x, -y.
 */
data class Corners(
    val topLeft: Double = 0.0,
    val topRight: Double = 0.0,
    val bottomRight: Double = 0.0,
    val bottomLeft: Double = 0.0,
) {
    /** All the radii, clockwise from the top left. */
    val radii: List<Double> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Common corner configurations. */
    companion object {
        /** The same [radius] on all the corners. */
        fun all(radius: Double): Corners = Corners(radius, radius, radius, radius)
    }
}
