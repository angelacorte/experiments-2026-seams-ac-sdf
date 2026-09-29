package it.unibo.collektive.sdf.impl.shapes.composite

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.impl.base.Arc
import kotlin.math.PI

/**
 * Represents a 2D Signed Distance Field (SDF) of an Archimedean-like spiral, made of semicircular arcs:
 * every half turn the radius grows by half of [spacing], so consecutive arms are [spacing] apart.
 * It winds counterclockwise, starting from the +x axis.
 *
 * @param center The (X, Y) coordinates of the spiral's center.
 * @param spacing The distance between two consecutive arms.
 * @param turns The number of turns.
 * @param innerRadius The radius at which the spiral starts (default is 0.0, starting from the center).
 * @param thickness The half-width of the stroke (default is 0.0).
 */
class Spiral(
    center: Position,
    spacing: Double,
    turns: Int,
    innerRadius: Double = 0.0,
    thickness: Double = 0.0,
) : SDF by Arc.tangentChain(center, List(2 * turns) { innerRadius + it * spacing / 2 }, PI, thickness)
