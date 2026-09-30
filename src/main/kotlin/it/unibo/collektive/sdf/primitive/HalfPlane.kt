package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.cross
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of a half plane: everything on the left of the line
 * going from [from] towards [to]. Intersected with other shapes, it cuts them along that line.
 *
 * @param from A point on the boundary line.
 * @param to Another point on the boundary line, giving its direction.
 */
class HalfPlane(private val from: Position, to: Position) : SDF {
    private val direction = to - from
    private val length = direction.norm

    init {
        require(length > 0.0) { "The two points of a half plane must differ, got $from twice" }
    }

    // Minus the distance from the line, so negative on its left.
    override fun invoke(position: Position): Double = -(direction cross (position - from)) / length
}
