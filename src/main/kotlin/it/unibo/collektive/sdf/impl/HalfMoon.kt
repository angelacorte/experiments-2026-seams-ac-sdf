package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.minus

/**
 * Represents a 2D Signed Distance Field (SDF) of a half moon (crescent):
 * a disk with a same-sized disk, shifted to the right by [offset], carved out.
 *
 * @param center The (X, Y) coordinates of the moon's disk center.
 * @param radius The radius of both disks.
 * @param offset The shift of the carved disk: the smaller, the thinner the crescent.
 */
class HalfMoon(center: Position, radius: Double, offset: Double) :
    SDF by (Circle(center, radius) - Circle(Position(center.x + offset, center.y), radius))
