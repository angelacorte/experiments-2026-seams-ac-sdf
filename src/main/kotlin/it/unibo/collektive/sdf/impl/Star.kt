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
 * @property radius The radius of the star.
 * @property n The number of points on the star.
 * @param m Determines how deep/profound the angles between the points are (default is n / 2.0).
 * @property isRing True if the space is a ring shaped like a star instead of a solid star (default is false).
 * @property thickness The thickness of the ring, or used to make round corners (default is 0.0).
 */
class Star(
    private val center: Position,
    private val radius: Double,
    private val n: Int,
    m: Double = n / 2.0,
    private val isRing: Boolean = false,
    private val thickness: Double = 0.0,
) : SDF {
    private val an = PI / n.toDouble()
    private val en = PI / m

    private val acsX = cos(an)
    private val acsY = sin(an)
    private val ecsX = cos(en)
    private val ecsY = sin(en)

    override fun invoke(position: Position): Double {
        var px = position.x - center.x
        var py = position.y - center.y

        val angle = atan2(px, py)
        val twoAn = 2.0 * an

        val bn = angle.mod(twoAn) - an

        val rPoint = sqrt(px * px + py * py)
        px = rPoint * cos(bn)
        py = rPoint * abs(sin(bn))

        px -= radius * acsX
        py -= radius * acsY

        val dot = px * ecsX + py * ecsY
        val maxClamp = radius * acsY / ecsY

        val clamped = (-dot).coerceIn(0.0, maxClamp)

        px += ecsX * clamped
        py += ecsY * clamped

        val distance = sqrt(px * px + py * py) * sign(px)
        return if (isRing) abs(distance) - thickness else distance - thickness
    }
}
