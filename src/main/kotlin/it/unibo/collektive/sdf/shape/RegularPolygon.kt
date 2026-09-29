package it.unibo.collektive.sdf.shape

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.polar
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Polygon
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular polygon.
 *
 * @param center The (X, Y) coordinates of the polygon's center.
 * @param radius The distance from the center to each vertex.
 * @param sides The number of sides.
 * @param rotation The angle of the first vertex (radians, counterclockwise from +x).
 */
class RegularPolygon(center: Position, radius: Double, sides: Int, rotation: Double = 0.0) :
    SDF by Polygon(
        run {
            require(radius > 0.0) { "Polygon radius must be positive, got $radius" }
            require(sides >= 3) { "A regular polygon needs at least three sides, got $sides" }
            List(sides) { center.polar(radius, rotation + 2 * PI * it / sides) }
        },
    )
