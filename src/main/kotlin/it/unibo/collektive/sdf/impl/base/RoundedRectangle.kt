package it.unibo.collektive.sdf.impl.base

import it.unibo.collektive.model.Position
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
 * @param topLeft The radius of the corner towards -x, +y.
 * @param topRight The radius of the corner towards +x, +y.
 * @param bottomRight The radius of the corner towards +x, -y.
 * @param bottomLeft The radius of the corner towards -x, -y.
 */
class RoundedRectangle(
    private val center: Position,
    width: Double,
    height: Double,
    private val topLeft: Double,
    private val topRight: Double,
    private val bottomRight: Double,
    private val bottomLeft: Double,
) : SDF {
    /** A rectangle with the same [radius] on all the corners. */
    constructor(center: Position, width: Double, height: Double, radius: Double) :
        this(center, width, height, radius, radius, radius, radius)

    private val halfWidth = width / 2
    private val halfHeight = height / 2

    override fun invoke(position: Position): Double {
        val x = position.x - center.x
        val y = position.y - center.y
        val radius = when {
            x > 0.0 && y > 0.0 -> topRight
            x > 0.0 -> bottomRight
            y > 0.0 -> topLeft
            else -> bottomLeft
        }
        // The rectangle shrunk by the radius, then grown back by it: this rounds the corner.
        val excessX = abs(x) - halfWidth + radius
        val excessY = abs(y) - halfHeight + radius
        return min(max(excessX, excessY), 0.0) + hypot(max(excessX, 0.0), max(excessY, 0.0)) - radius
    }
}
