package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Represents a 2D Signed Distance Field (SDF) of a crescent:
 * a disk with a same-sized disk, shifted to the right by [offset], carved out.
 *
 * The distance is exact (Inigo Quilez's "moon"), unlike `Circle - Circle`: that difference, `max(A, -B)`, is only a
 * bound, and outside the shape it has a valley of local minima (the ellipse with the two centers as foci, through the
 * tips) where the nodes following its gradient get stuck. Here the points beyond the tips measure the distance from
 * the nearest tip instead.
 *
 * @param center The (X, Y) coordinates of the moon's disk center.
 * @param radius The radius of both disks.
 * @param offset The shift of the carved disk: the smaller, the thinner the crescent.
 */
class Crescent(private val center: Position, private val radius: Double, private val offset: Double) : SDF {
    init {
        require(radius > 0.0) { "Crescent radius must be positive, got $radius" }
        require(offset > 0.0 && offset < 2 * radius) {
            "Crescent offset must be in (0, ${2 * radius}), got $offset"
        }
    }

    // The upper tip, relative to the center: where the two circles cross (same radius, so halfway along x).
    private val tipX = offset / 2
    private val tipY = sqrt(max(radius * radius - tipX * tipX, 0.0))

    override fun invoke(position: Position): Double {
        val x = position.x - center.x
        val y = abs(position.y - center.y) // Symmetric about the horizontal axis
        return if (offset * (x * tipY - y * tipX) > offset * offset * max(tipY - y, 0.0)) {
            hypot(x - tipX, y - tipY) // Beyond the tips: the nearest point is the tip itself
        } else {
            max(hypot(x, y) - radius, radius - hypot(x - offset, y))
        }
    }
}
