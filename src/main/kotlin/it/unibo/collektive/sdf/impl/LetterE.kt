package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF

/**
 * Represents a 2D Signed Distance Field (SDF) of the letter E.
 *
 * @param start The (X, Y) coordinates of the starting point (bottom-left) of the vertical stem.
 * @param height The total height of the letter.
 * @property thickness The thickness of the letter's strokes (default is 0.0).
 */
class LetterE(
    start: Position,
    height: Double,
    private val thickness: Double = 0.0,
) : SDF {
    private val verticalStem = Segment(
        start,
        Position(start.x, start.y + height),
    )
    private val lowerArm = Segment(
        start,
        Position(start.x + height * HALF, start.y),
    )
    private val middleArm = Segment(
        Position(start.x, start.y + height * HALF),
        Position(
            start.x + height * THREE_EIGHTHS,
            start.y + height * HALF,
        ),
    )
    private val upperArm = Segment(
        Position(start.x, start.y + height),
        Position(start.x + height * HALF, start.y + height),
    )

    override fun invoke(position: Position): Double = minOf(
        verticalStem(position),
        lowerArm(position),
        middleArm(position),
        upperArm(position),
    ) - thickness

    companion object {
        private const val HALF = 0.5
        private const val THREE_EIGHTHS = 0.375
    }
}
