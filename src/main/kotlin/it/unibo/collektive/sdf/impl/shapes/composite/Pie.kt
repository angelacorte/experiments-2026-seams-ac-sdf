package it.unibo.collektive.sdf.impl.shapes.composite

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.and
import it.unibo.collektive.sdf.impl.base.Circle

/**
 * Represents a 2D Signed Distance Field (SDF) of a circular sector (a slice of pie), opening towards +y:
 * a disk intersected with a [Wedge] with the tip on its center.
 *
 * @param center The (X, Y) coordinates of the tip of the sector.
 * @param radius The radius of the sector.
 * @param halfAperture Half of the angle of the sector in radians, measured from the +y axis: π / 2 gives
 * a half disk, π a full disk.
 */
class Pie(center: Position, radius: Double, halfAperture: Double) :
    SDF by (Circle(center, radius) and Wedge(center, halfAperture))
