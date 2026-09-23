package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.euclideanDistanceTo
import it.unibo.collektive.sdf.SDF
import kotlin.math.abs

/**
 * Represents a 2D Signed Distance Field (SDF) of a circle or a ring.
 *
 * @property center The (X, Y) coordinates of the circle's center.
 * @property radius The radius of the circle.
 * @property isRing True if the shape is a hollow ring instead of a solid circle (default is false).
 * @property thickness The thickness of the ring if [isRing] is true (default is 0.0).
 */
class Circle(
    private val center: Position,
    private val radius: Double,
    private val isRing: Boolean = false,
    private val thickness: Double = 0.0,
) : SDF {
    override fun invoke(position: Position): Double {
        val circleDist = position.euclideanDistanceTo(center) - radius

        return if (isRing) abs(circleDist) - thickness else circleDist
    }
}
