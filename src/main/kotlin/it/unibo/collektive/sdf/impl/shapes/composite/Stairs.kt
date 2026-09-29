package it.unibo.collektive.sdf.impl.shapes.composite

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.impl.base.Rectangle
import it.unibo.collektive.sdf.union

/**
 * Represents a 2D Signed Distance Field (SDF) of a solid staircase going up towards +x:
 * one column per step, each as tall as all the steps up to it.
 *
 * @param origin The (X, Y) coordinates of the bottom-left corner, where the staircase starts.
 * @param stepWidth The horizontal length of each step.
 * @param stepHeight The height of each step.
 * @param steps The number of steps.
 */
class Stairs(origin: Position, stepWidth: Double, stepHeight: Double, steps: Int) : SDF by (
    run {
        require(steps > 0) { "A staircase needs at least one step, got $steps" }
        List(steps) { step ->
            val height = (step + 1) * stepHeight
            Rectangle(Position(origin.x + (step + 0.5) * stepWidth, origin.y + height / 2), stepWidth, height)
        }.union()
    }
)
