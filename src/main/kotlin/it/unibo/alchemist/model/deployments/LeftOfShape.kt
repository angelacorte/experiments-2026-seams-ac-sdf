package it.unibo.alchemist.model.deployments

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.sdf.SDF
import org.apache.commons.math3.random.RandomGenerator
import it.unibo.collektive.geometry.Position as Point

/**
 * Deploys [nodeCount] nodes uniformly at random in a rectangle entirely to the left of the shape called [shape]
 * in `shapes.yml` (where it lies with global positions), so that no node starts inside it.
 *
 * The rectangle is [width] wide, its right side lies [gap] to the left of the leftmost point of the shape, and it
 * spans the vertical extent of the shape. The extent is found by sampling the SDF on a grid of side [SAMPLE_STEP]
 * over [[SAMPLE_FROM], [SAMPLE_TO]]^2, and the rectangle is moved further left by one step to absorb the error;
 * every position is checked against the SDF anyway, and redrawn if it falls inside the shape.
 *
 * @param P the type of positions
 */
class LeftOfShape<P : Position<out P>> @JvmOverloads constructor(
    environment: Environment<*, P>,
    randomGenerator: RandomGenerator,
    nodeCount: Int,
    shape: String,
    private val width: Double,
    private val gap: Double = 1.0,
) : AbstractRandomDeployment<P>(environment, randomGenerator, nodeCount) {
    private val sdf: SDF = ShapeCatalog.named(shape).sdf
    private val right: Double
    private val bottom: Double
    private val height: Double

    init {
        require(width > 0.0) { "The width of the deployment must be positive, got $width" }
        require(gap >= 0.0) { "The gap from the shape cannot be negative, got $gap" }
        val cells = ((SAMPLE_TO - SAMPLE_FROM) / SAMPLE_STEP).toInt()
        val inside = (0..cells).flatMap { i ->
            (0..cells).mapNotNull { j ->
                Point(SAMPLE_FROM + i * SAMPLE_STEP, SAMPLE_FROM + j * SAMPLE_STEP).takeIf(sdf::isInside)
            }
        }
        check(inside.isNotEmpty()) { "Shape '$shape' has no point within [$SAMPLE_FROM, $SAMPLE_TO]^2" }
        right = inside.minOf(Point::x) - SAMPLE_STEP - gap
        bottom = inside.minOf(Point::y) - SAMPLE_STEP
        height = inside.maxOf(Point::y) + SAMPLE_STEP - bottom
    }

    override fun indexToPosition(i: Int): P {
        repeat(MAX_ATTEMPTS) {
            val x = randomDouble(right - width, right)
            val y = randomDouble(bottom, bottom + height)
            if (sdf.isOutside(Point(x, y))) {
                return makePosition(x, y)
            }
        }
        error("Could not place a node outside the shape after $MAX_ATTEMPTS attempts")
    }

    private companion object {
        private const val SAMPLE_FROM = -100.0
        private const val SAMPLE_TO = 200.0
        private const val SAMPLE_STEP = 0.5
        private const val MAX_ATTEMPTS = 1000
    }
}
