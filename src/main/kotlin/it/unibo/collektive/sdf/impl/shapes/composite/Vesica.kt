package it.unibo.collektive.sdf.impl.shapes.composite

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.and
import it.unibo.collektive.sdf.impl.base.Circle

/**
 * Represents a 2D Signed Distance Field (SDF) of a vesica piscis: the lens where two disks of the same [radius],
 * shifted left and right of [center], overlap. Its tips lie on the vertical axis.
 *
 * @param center The (X, Y) coordinates of the lens' center.
 * @param radius The radius of both disks.
 * @param offset How far the centers of the disks are from [center] along x: from 0 (a circle) up to [radius]
 * (excluded), where the lens gets thinner and thinner.
 */
class Vesica(center: Position, radius: Double, offset: Double) : SDF by (
    run {
        require(offset >= 0.0 && offset < radius) { "The offset must be in [0, radius), got $offset for $radius" }
        Circle(Position(center.x - offset, center.y), radius) and Circle(Position(center.x + offset, center.y), radius)
    }
)
