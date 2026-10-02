package it.unibo.alchemist.model.reactions

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import org.apache.commons.math3.random.RandomGenerator

/**
 * At simulated [time], adds [count] nodes in uniformly random positions inside the rectangle
 * with bottom-left corner ([x], [y]), [width] and [height] (see [AbstractSpawnNodes]).
 *
 * @param T the type of the concentrations
 * @param P the type of positions
 */
class SpawnRandomNodes<T, P : Position<P>>(
    environment: Environment<T, P>,
    private val randomGenerator: RandomGenerator,
    time: Double,
    count: Int,
    private val x: Double,
    private val y: Double,
    private val width: Double,
    private val height: Double,
) : AbstractSpawnNodes<T, P>(environment, time, count) {
    override fun nextPosition(existing: List<P>): P = typedEnvironment.makePosition(
        x + randomGenerator.nextDouble() * width,
        y + randomGenerator.nextDouble() * height,
    )
}
