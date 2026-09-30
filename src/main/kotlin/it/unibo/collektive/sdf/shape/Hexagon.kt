package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF

private const val HEXAGON_SIDES = 6

/**
 * Represents a 2D Signed Distance Field (SDF) of a regular hexagon.
 *
 * @param center The (X, Y) coordinates of the hexagon's center.
 * @param radius The distance from the center to each vertex.
 */
class Hexagon(center: Position, radius: Double) : SDF by RegularPolygon(center, radius, HEXAGON_SIDES)
