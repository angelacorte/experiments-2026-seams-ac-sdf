package it.unibo.collektive.sdf.impl.shapes.simple

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.impl.base.Triangle
import it.unibo.collektive.sdf.or

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
