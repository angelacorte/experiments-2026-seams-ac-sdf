package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.or
import it.unibo.collektive.sdf.union
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The point at [radius] from [center], along [angle] (radians, counterclockwise from +x). */
fun polar(center: Position, radius: Double, angle: Double) =
    Position(center.x + radius * cos(angle), center.y + radius * sin(angle))

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned rectangle: two triangles split along a diagonal.
 *
 * @param center The (X, Y) coordinates of the rectangle's center.
 * @param width The horizontal side.
 * @param height The vertical side.
 */
class Rectangle(center: Position, width: Double, height: Double) : SDF by (
    run {
        val (left, right) = center.x - width / 2 to center.x + width / 2
        val (bottom, top) = center.y - height / 2 to center.y + height / 2
        Triangle(Position(left, bottom), Position(right, bottom), Position(right, top)) or
            Triangle(Position(left, bottom), Position(right, top), Position(left, top))
    }
)

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned square.
 *
 * @param center The (X, Y) coordinates of the square's center.
 * @param side The side length.
 */
class Square(center: Position, side: Double) : SDF by Rectangle(center, side, side)

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular polygon: one triangle per side, fanning from the center.
 *
 * @param center The (X, Y) coordinates of the polygon's center.
 * @param radius The distance from the center to each vertex.
 * @param sides The number of sides.
 * @param rotation The angle of the first vertex (radians, counterclockwise from +x).
 */
class RegularPolygon(center: Position, radius: Double, sides: Int, rotation: Double = 0.0) : SDF by (
    (0..sides).map { polar(center, radius, rotation + 2 * PI * it / sides) }
        .zipWithNext { from, to -> Triangle(center, from, to) }
        .union()
)

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular hexagon.
 *
 * @param center The (X, Y) coordinates of the hexagon's center.
 * @param radius The distance from the center to each vertex.
 */
class Hexagon(center: Position, radius: Double) : SDF by RegularPolygon(center, radius, 6)
