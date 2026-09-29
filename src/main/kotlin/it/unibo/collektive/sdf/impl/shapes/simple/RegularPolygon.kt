package it.unibo.collektive.sdf.impl.shapes.simple

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.polar
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.impl.base.Triangle
import it.unibo.collektive.sdf.union
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular polygon: one triangle per side, fanning from the center.
 *
 * @param center The (X, Y) coordinates of the polygon's center.
 * @param radius The distance from the center to each vertex.
 * @param sides The number of sides.
 * @param rotation The angle of the first vertex (radians, counterclockwise from +x).
 */
class RegularPolygon(center: Position, radius: Double, sides: Int, rotation: Double = 0.0) : SDF by (
    (0..sides).map { center.polar(radius, rotation + 2 * PI * it / sides) }
        .zipWithNext { from, to -> Triangle(center, from, to) }
        .union()
)
