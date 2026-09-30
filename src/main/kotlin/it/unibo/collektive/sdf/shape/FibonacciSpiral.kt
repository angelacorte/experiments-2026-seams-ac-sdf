package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI

/** The first [count] Fibonacci numbers: 1, 1, 2, 3, 5, 8... */
private fun fibonacci(count: Int): List<Long> = generateSequence(1L to 1L) { (a, b) -> b to a + b }
    .map { it.first }
    .take(count)
    .toList()

/**
 * Represents a 2D Signed Distance Field (SDF) of a Fibonacci (golden) spiral: quarter circles whose radii are
 * the Fibonacci numbers, each fitting in the next square of the classic Fibonacci tiling.
 * It winds counterclockwise, starting from the +x axis.
 *
 * @param center The (X, Y) coordinates of the center of the innermost arc.
 * @param scale The radius of the first two arcs, i.e., the side of the smallest squares.
 * @param quarterTurns The number of quarter circles.
 * @param thickness The half-width of the stroke (default is 0.0).
 */
class FibonacciSpiral(center: Position, scale: Double, quarterTurns: Int, thickness: Double = 0.0) :
    SDF by tangentArcChain(
        center = center,
        radii = run {
            require(scale > 0.0) { "Fibonacci spiral scale must be positive, got $scale" }
            require(quarterTurns > 0) { "A Fibonacci spiral needs at least one quarter turn, got $quarterTurns" }
            fibonacci(quarterTurns).map { it * scale }
        },
        sweep = PI / 2,
        thickness = thickness,
    )
