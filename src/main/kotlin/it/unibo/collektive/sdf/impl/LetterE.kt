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
class LetterE(start: Position, height: Double, private val thickness: Double = 0.0) : SDF {
    private val vertical = Segment(start, Position(start.x, start.y + height))
    private val horizontalLow = Segment(start, Position(start.x + height / HALF_DIVISOR, start.y))
    private val horizontalCenter =
        Segment(
            Position(start.x, start.y + height / HALF_DIVISOR),
            Position(start.x + height * THREE_EIGHTHS, start.y + height / HALF_DIVISOR),
        )
    private val horizontalHigh =
        Segment(
            Position(start.x, start.y + height),
            Position(start.x + height / HALF_DIVISOR, start.y + height),
        )

    override fun invoke(position: Position): Double = minOf(
        vertical(position),
        horizontalLow(position),
        horizontalCenter(position),
        horizontalHigh(position),
    ) - thickness

    /**
     * Constants used for proportional geometry calculations.
     */
    companion object {
        private const val HALF_DIVISOR = 2.0
        private const val THREE_EIGHTHS = 0.375
    }
}
