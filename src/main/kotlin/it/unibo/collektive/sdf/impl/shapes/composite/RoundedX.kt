package it.unibo.collektive.sdf.impl.shapes.composite

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.expand
import it.unibo.collektive.sdf.impl.base.Segment
import it.unibo.collektive.sdf.or

/**
 * Represents a 2D Signed Distance Field (SDF) of an X with rounded ends: its two diagonals, thickened by [radius].
 *
 * @param center The (X, Y) coordinates of the crossing.
 * @param width The side of the square the diagonals span, not counting the rounding.
 * @param radius The half-width of the arms.
 */
class RoundedX(center: Position, width: Double, radius: Double) : SDF by (
    run {
        val half = width / 2
        fun corner(signX: Int, signY: Int) = Position(center.x + signX * half, center.y + signY * half)
        (Segment(corner(-1, -1), corner(1, 1)) or Segment(corner(-1, 1), corner(1, -1))) expand radius
    }
)
