package it.unibo.alchemist.model.reactions

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.apache.commons.math3.random.RandomGenerator

/**
 * At simulated [time], adds [count] nodes close to the ones already present (see [AbstractSpawnNodes]),
 * so that each of them can communicate with at least one of them as soon as it appears.
 *
 * Each node is placed uniformly at random among the points within `communicationRange * rangeFraction`
 * of at least one existing node (the union of the disks around them).
 * The point is drawn inside the bounding box of the existing nodes, enlarged by that distance,
 * and discarded if it is too far from all of them. After [MAX_ATTEMPTS] discarded points,
 * the node is placed in the disk around a random existing node.
 *
 * @param T the type of the concentrations
 * @param P the type of positions
 */
class SpawnNearNodes<T, P : Position<P>>(
    environment: Environment<T, P>,
    private val randomGenerator: RandomGenerator,
    time: Double,
    count: Int,
    communicationRange: Double,
    rangeFraction: Double,
) : AbstractSpawnNodes<T, P>(environment, time, count) {
    private val maxDistance = communicationRange * rangeFraction

    init {
        require(maxDistance > 0) { "The spawn distance must be positive, got $maxDistance" }
    }

    override fun nextPosition(existing: List<P>): P {
        check(existing.isNotEmpty()) { "SpawnNearNodes needs at least one existing node" }
        val xs = existing.map { it.getCoordinate(0) }
        val ys = existing.map { it.getCoordinate(1) }
        val minX = xs.min() - maxDistance
        val minY = ys.min() - maxDistance
        val width = xs.max() + maxDistance - minX
        val height = ys.max() + maxDistance - minY
        repeat(MAX_ATTEMPTS) {
            val candidate = typedEnvironment.makePosition(
                minX + randomGenerator.nextDouble() * width,
                minY + randomGenerator.nextDouble() * height,
            )
            if (existing.any { position -> position.distanceTo(candidate) <= maxDistance }) {
                return candidate
            }
        }
        val anchor = existing[randomGenerator.nextInt(existing.size)]
        val radius = maxDistance * sqrt(randomGenerator.nextDouble())
        val angle = 2 * PI * randomGenerator.nextDouble()
        return typedEnvironment.makePosition(
            anchor.getCoordinate(0) + radius * cos(angle),
            anchor.getCoordinate(1) + radius * sin(angle),
        )
    }

    private companion object {
        private const val MAX_ATTEMPTS = 1000
    }
}
