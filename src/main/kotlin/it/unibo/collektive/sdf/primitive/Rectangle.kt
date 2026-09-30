package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned rectangle, as in Inigo Quilez's `sdBox`:
 * a [RoundedRectangle] with sharp corners.
 *
 * @param center The (X, Y) coordinates of the rectangle's center.
 * @param width The horizontal side.
 * @param height The vertical side.
 */
class Rectangle(center: Position, width: Double, height: Double) :
    SDF by (
        run {
            require(width > 0.0 && height > 0.0) { "Rectangle sides must be positive, got $width × $height" }
            RoundedRectangle(center, width, height, radius = 0.0)
        }
        )
