package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.ring

/**
 * Represents a 2D Signed Distance Field (SDF) of a circular band.
 *
 * @param center The (X, Y) coordinates of the center of the circle the band follows.
 * @param radius The radius of the circle the band follows.
 * @param thickness The half-width of the band.
 */
class Ring(center: Position, radius: Double, thickness: Double) : SDF by (Circle(center, radius) ring thickness)
