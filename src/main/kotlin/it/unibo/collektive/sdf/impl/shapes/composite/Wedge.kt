package it.unibo.collektive.sdf.impl.shapes.composite

import it.unibo.collektive.model.Position
import it.unibo.collektive.model.polar
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.and
import it.unibo.collektive.sdf.impl.base.HalfPlane
import it.unibo.collektive.sdf.or
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of an infinite angular wedge with the tip at [tip],
 * opening towards +y: the two half planes bounded by its sides, intersected when the wedge is convex
 * (up to a half plane) and joined when it is wider.
 *
 * @param tip The (X, Y) coordinates of the wedge's tip.
 * @param halfAperture Half of the angle of the wedge in radians, measured from the +y axis, between 0 and π.
 */
class Wedge(tip: Position, halfAperture: Double) : SDF by (
    run {
        val rightSide = HalfPlane(tip, tip.polar(1.0, PI / 2 - halfAperture))
        val leftSide = HalfPlane(tip.polar(1.0, PI / 2 + halfAperture), tip)
        if (halfAperture <= PI / 2) rightSide and leftSide else rightSide or leftSide
    }
)
