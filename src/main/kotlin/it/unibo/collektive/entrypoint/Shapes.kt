package it.unibo.collektive.entrypoint

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.ring
import it.unibo.collektive.sdf.shape.Star

// private val shape = "HELLO\nWORLD"
// .toSdf(start = Position(-20.0, 30.0), height = 40.0, thickness = 4.2, spacing = 10.5,).scale(0.5)
// private val shape = Star(Position.origin, 75.0, 5, 3.0)
/**
 * Shape used for simulation purposes.
 */
val shape = Star(Position.origin, 30.0, 5) ring 3.5
// FibonacciSpiral(Position(50.0, 50.0), 15.0, 6, 5.0)
// Spiral(Position(0.0, 0.0), 10.0, 2, 10.0, 2.0).scale(3.0)
// val shape = Stairs(Position(0.0, 0.0), 20.0, 20.0, 5)
// val shape = FibonacciSpiral(Position(60.0, 68.0), scale = 2.0, quarterTurns = 12, thickness = 3.0)
// val shape = Spiral(Position(0.0, 0.0), spacing = 20.0, turns = 4, innerRadius = 10.0, thickness = 5.0)
// val shape = Star(Position(50.0, 50.0), 75.0, 5, 3.0)
// val shape = Circle(Position(50.0, 50.0), 40.0)
// val shape = QuestionMark(Position(100.0, 100.0), 30.0, 10.0)
// val shape = Triangle(Position(0.0, 0.0), Position(200.0, 0.0), Position(100.0, 200.0))
