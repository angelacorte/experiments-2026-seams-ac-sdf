package it.unibo.collektive.sdf.impl.shapes.simple

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular hexagon.
 *
 * @param center The (X, Y) coordinates of the hexagon's center.
 * @param radius The distance from the center to each vertex.
 */
class Hexagon(center: Position, radius: Double) : SDF by RegularPolygon(center, radius, 6)
