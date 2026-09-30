package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned rectangle, as in Inigo Quilez's `sdBox`.
 *
 * @param center The (X, Y) coordinates of the rectangle's center.
 * @param width The horizontal side.
 * @param height The vertical side.
 */
class Rectangle(private val center: Position, width: Double, height: Double) : SDF {
    init {
        require(width > 0.0 && height > 0.0) { "Rectangle sides must be positive, got $width × $height" }
    }

    private val halfWidth = width / 2
    private val halfHeight = height / 2

    override fun invoke(position: Position): Double {
        val excessX = abs(position.x - center.x) - halfWidth
        val excessY = abs(position.y - center.y) - halfHeight
        return hypot(max(excessX, 0.0), max(excessY, 0.0)) + min(max(excessX, excessY), 0.0)
    }
}
