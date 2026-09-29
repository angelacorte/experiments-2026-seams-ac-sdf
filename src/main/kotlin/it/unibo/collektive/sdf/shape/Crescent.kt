package it.unibo.collektive.sdf.shape

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.minus

/**
 * Represents a 2D Signed Distance Field (SDF) of a crescent:
 * a disk with a same-sized disk, shifted to the right by [offset], carved out.
 *
 * @param center The (X, Y) coordinates of the moon's disk center.
 * @param radius The radius of both disks.
 * @param offset The shift of the carved disk: the smaller, the thinner the crescent.
 */
class Crescent(center: Position, radius: Double, offset: Double) : SDF by (
    run {
        require(radius > 0.0) { "Crescent radius must be positive, got $radius" }
        require(offset > 0.0 && offset < 2 * radius) {
            "Crescent offset must be in (0, ${2 * radius}), got $offset"
        }
        Circle(center, radius) - Circle(Position(center.x + offset, center.y), radius)
    }
)

