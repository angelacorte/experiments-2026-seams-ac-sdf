package it.unibo.collektive.sdf.impl.shapes.simple

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.impl.base.Rectangle

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned square.
 *
 * @param center The (X, Y) coordinates of the square's center.
 * @param side The side length.
 */
class Square(center: Position, side: Double) : SDF by Rectangle(center, side, side)
