package it.unibo.collektive.sdf.primitive

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.hypot

/**
 * Represents a 2D Signed Distance Field (SDF) of a half plane: everything on the left of the line
 * going from [from] towards [to]. Intersected with other shapes, it cuts them along that line.
 *
 * @param from A point on the boundary line.
 * @param to Another point on the boundary line, giving its direction.
 */
class HalfPlane(private val from: Position, to: Position) : SDF {
    private val directionX = to.x - from.x
    private val directionY = to.y - from.y
    private val length = hypot(directionX, directionY)

    init {
        require(length > 0.0) { "The two points of a half plane must differ, got $from twice" }
    }

    override fun invoke(position: Position): Double = // Minus the distance from the line, positive on its left.
        -(directionX * (position.y - from.y) - directionY * (position.x - from.x)) / length
}
