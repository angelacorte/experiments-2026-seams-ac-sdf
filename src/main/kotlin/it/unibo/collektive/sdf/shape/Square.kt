package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Rectangle

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned square.
 *
 * @param center The (X, Y) coordinates of the square's center.
 * @param side The side length.
 */
class Square(center: Position, side: Double) : SDF by Rectangle(center, side, side)
