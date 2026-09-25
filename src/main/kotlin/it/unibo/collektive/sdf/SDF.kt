package it.unibo.collektive.sdf

import it.unibo.collektive.model.Position

/**
 * Represents a 2D Signed Distance Field (SDF).
 * A functional interface that evaluates the signed distance from a given point to the boundary of a shape.
 */
fun interface SDF {
    /**
     * Returns the distance of [position] from the surface of the SDF.
     * if the value is negative, the point is inside the SDF.
     */
    operator fun invoke(position: Position): Double

    fun isInside(position: Position): Boolean = this(position) <= 0.0
}

/**
 * Creates a new Signed Distance Field (SDF) representing the inverse of the given [shape].
 * * By negating the distance value, the internal regions (traditionally negative) become
 * external (positive), and the external regions become internal.
 */
fun inverseSDF(shape: SDF): SDF = SDF { position -> -shape(position) }
