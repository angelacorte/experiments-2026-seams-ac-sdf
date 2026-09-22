package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a 2D Signed Distance Field (SDF) of a star shape or a star-shaped ring.
 *
 * @property center The (X, Y) coordinates of the star's center.
 * @property radius The outer radius of the star.
 * @property pointCount The number of points of the star.
 * @param spikiness Controls how sharp and pronounced the star's points are.
 * Must be between 2 and [pointCount]. Lower values produce a shape closer
 * to a regular polygon, while higher values produce sharper indentations.
 * @property isRing True if the SDF represents a star-shaped ring instead of a solid star.
 * @property thickness The thickness of the ring, or the amount used to round the boundary.
*/
class Star(
    private val center: Position,
    private val radius: Double,
    private val pointCount: Int,
    spikiness: Double = pointCount / 2.0,
    private val isRing: Boolean = false,
    private val thickness: Double = 0.0,
) : SDF {
    private val halfSectorAngle = PI / pointCount.toDouble()
    private val edgeAngle = PI / spikiness
    private val sectorDirectionX = cos(halfSectorAngle)
    private val sectorDirectionY = sin(halfSectorAngle)
    private val edgeDirectionX = cos(edgeAngle)
    private val edgeDirectionY = sin(edgeAngle)

    override fun invoke(position: Position): Double {
        var localX = position.x - center.x
        var localY = position.y - center.y
        val angle = atan2(localX, localY)
        val sectorAngle = 2.0 * halfSectorAngle
        val foldedAngle = angle.mod(sectorAngle) - halfSectorAngle
        val distanceFromCenter = sqrt(localX * localX + localY * localY)

        localX = distanceFromCenter * cos(foldedAngle)
        localY = distanceFromCenter * abs(sin(foldedAngle))
        localX -= radius * sectorDirectionX
        localY -= radius * sectorDirectionY

        val edgeProjection = localX * edgeDirectionX + localY * edgeDirectionY
        val maxEdgeProjection = radius * sectorDirectionY / edgeDirectionY
        val edgeOffset = (-edgeProjection).coerceIn(0.0, maxEdgeProjection)

        localX += edgeDirectionX * edgeOffset
        localY += edgeDirectionY * edgeOffset

        val signedDistance = sqrt(localX * localX + localY * localY) * sign(localX)
        return when {
            isRing -> abs(signedDistance) - thickness
            else -> signedDistance - thickness
        }
    }
}
