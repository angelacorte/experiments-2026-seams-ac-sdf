package it.unibo.collektive.sdf.impl

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of the letter B.
 *
 * @param start The (X, Y) coordinates of the starting point of the vertical stem.
 * @param height The total height of the letter.
 * @property thickness The thickness of the letter's strokes (default is 0.0).
 */
class LetterB(start: Position, height: Double, private val thickness: Double = 0.0) : SDF {
    private val verticalStem = Segment(start, Position(start.x, start.y + height))

    private val lowerArc = Arc(
        Position(
            start.x + height / QUARTER_DIVISOR,
            start.y + height / QUARTER_DIVISOR,
        ),
        height / QUARTER_DIVISOR,
        -PI / HALF_DIVISOR,
        PI,
    )

    private val upperArc = Arc(
        Position(
            start.x + height / EIGHTH_DIVISOR,
            start.y + height * THREE_QUARTERS,
        ),
        height / QUARTER_DIVISOR,
        -PI / HALF_DIVISOR,
        PI,
    )

    private val lowerSegment = Segment(
        start,
        Position(start.x + height / QUARTER_DIVISOR, start.y),
    )

    private val middleSegment = Segment(
        Position(start.x, start.y + height / HALF_DIVISOR),
        Position(
            start.x + height / QUARTER_DIVISOR,
            start.y + height / HALF_DIVISOR,
        ),
    )

    private val upperSegment = Segment(
        Position(start.x, start.y + height),
        Position(start.x + height / EIGHTH_DIVISOR, start.y + height),
    )

    override fun invoke(position: Position): Double = minOf(
        verticalStem(position),
        upperArc(position),
        lowerArc(position),
        lowerSegment(position),
        middleSegment(position),
        upperSegment(position),
    ) - thickness

    /** Constants defining the proportions of the letter. */
    companion object {
        private const val HALF_DIVISOR = 2.0
        private const val QUARTER_DIVISOR = 4.0
        private const val EIGHTH_DIVISOR = 8.0
        private const val THREE_QUARTERS = 0.75
    }
}
