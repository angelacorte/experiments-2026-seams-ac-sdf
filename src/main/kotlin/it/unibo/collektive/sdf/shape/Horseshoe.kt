package it.unibo.collektive.sdf.shape

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.polar
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.primitive.Rectangle
import it.unibo.collektive.sdf.minus
import it.unibo.collektive.sdf.or
import it.unibo.collektive.sdf.ring
import it.unibo.collektive.sdf.rotate
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of a horseshoe opening towards +y: a circular band with a
 * [CircularSector]
 * carved out at the top, and two straight arms with flat tips continuing it, tangent to the circle.
 *
 * @param center The (X, Y) coordinates of the center of the circle the band follows.
 * @param radius The radius of the circle the band follows.
 * @param aperture Half of the angle left open at the top, in radians, measured from the +y axis.
 * @param armLength How much the arms extend, straight, past the end of the band.
 * @param thickness The half-width of the band.
 */
class Horseshoe(center: Position, radius: Double, aperture: Double, armLength: Double, thickness: Double) :
    SDF by (
        run {
            require(radius > 0.0) { "Horseshoe radius must be positive, got $radius" }
            require(aperture > 0.0 && aperture < PI) {
                "Horseshoe aperture must be in (0, π), got $aperture"
            }
            require(armLength >= 0.0) { "Horseshoe arm length cannot be negative, got $armLength" }
            require(thickness >= 0.0) { "Horseshoe thickness cannot be negative, got $thickness" }
            val band =
                (Circle(center, radius) ring thickness) -
                    CircularSector(center, 2 * (radius + thickness), aperture)

            /** An arm leaving the band at [end], going along [direction] (radians, counterclockwise from +x). */
            fun arm(end: Position, direction: Double): SDF {
                val middle = end.polar(armLength / 2, direction)
                return Rectangle(middle, armLength, 2 * thickness).rotate(direction, middle)
            }
            band or
                arm(center.polar(radius, PI / 2 - aperture), PI - aperture) or
                arm(center.polar(radius, PI / 2 + aperture), aperture)
        }
    )
