package it.unibo.alchemist.model.deployments

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.geometry.Position as Point
import org.apache.commons.math3.random.RandomGenerator

/**
 * Deploys [nodeCount] nodes uniformly at random in a rectangle placed with respect to the shape called [shape] in
 * `shapes.yml` (where it lies with global positions). The [start] chooses where, so that a single simulation file can
 * batch over it:
 * - `left`: a rectangle [width] wide, entirely to the left of the shape, so that no node starts inside it. Its right
 *   side lies [gap] to the left of the leftmost point of the shape, and it spans the vertical extent of the shape. The
 *   extent is found by sampling the SDF on a grid of side [SAMPLE_STEP] over [[SAMPLE_FROM], [SAMPLE_TO]]^2, and the
 *   rectangle is moved further left by one step to absorb the error; every position is also checked against the SDF,
 *   and redrawn if it falls inside the shape.
 * - `centered`: a square of [side] centered on the center of the shape (its `center`, `origin` or `start`), as the
 *   classic `Rectangle` deployment: with a shape centered on (50, 50) and a [side] of 100, it is [0, 100]^2.
 *
 * @param P the type of positions
 */
class ShapeStart<P : Position<out P>> @JvmOverloads constructor(
    environment: Environment<*, P>,
    randomGenerator: RandomGenerator,
    nodeCount: Int,
    shape: String,
    private val start: String,
    private val width: Double,
    private val side: Double,
    private val gap: Double = 1.0,
) : AbstractRandomDeployment<P>(environment, randomGenerator, nodeCount) {
    private val target = ShapeCatalog.named(shape)
    private val left: Double
    private val bottom: Double
    private val areaWidth: Double
    private val areaHeight: Double

    init {
        require(width > 0.0 && side > 0.0) { "The deployment sizes must be positive, got width $width, side $side" }
        require(gap >= 0.0) { "The gap from the shape cannot be negative, got $gap" }
        when (start) {
            LEFT -> {
                val cells = ((SAMPLE_TO - SAMPLE_FROM) / SAMPLE_STEP).toInt()
                val inside = (0..cells).flatMap { i ->
                    (0..cells).mapNotNull { j ->
                        Point(SAMPLE_FROM + i * SAMPLE_STEP, SAMPLE_FROM + j * SAMPLE_STEP)
                            .takeIf(target.sdf::isInside)
                    }
                }
                check(inside.isNotEmpty()) { "Shape '$shape' has no point within [$SAMPLE_FROM, $SAMPLE_TO]^2" }
                left = inside.minOf(Point::x) - SAMPLE_STEP - gap - width
                bottom = inside.minOf(Point::y) - SAMPLE_STEP
                areaWidth = width
                areaHeight = inside.maxOf(Point::y) + SAMPLE_STEP - bottom
            }
            CENTERED -> {
                left = target.center.x - side / 2
                bottom = target.center.y - side / 2
                areaWidth = side
                areaHeight = side
            }
            else -> error("Unknown start '$start', expected '$LEFT' or '$CENTERED'")
        }
    }

    override fun indexToPosition(i: Int): P {
        if (start == CENTERED) {
            return makePosition(randomDouble(left, left + areaWidth), randomDouble(bottom, bottom + areaHeight))
        }
        repeat(MAX_ATTEMPTS) {
            val x = randomDouble(left, left + areaWidth)
            val y = randomDouble(bottom, bottom + areaHeight)
            if (target.sdf.isOutside(Point(x, y))) {
                return makePosition(x, y)
            }
        }
        error("Could not place a node outside the shape after $MAX_ATTEMPTS attempts")
    }

    private companion object {
        private const val LEFT = "left"
        private const val CENTERED = "centered"
        private const val SAMPLE_FROM = -100.0
        private const val SAMPLE_TO = 200.0
        private const val SAMPLE_STEP = 0.5
        private const val MAX_ATTEMPTS = 1000
    }
}
