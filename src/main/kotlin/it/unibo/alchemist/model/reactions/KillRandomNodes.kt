package it.unibo.alchemist.model.reactions

import it.unibo.alchemist.model.Environment
import org.apache.commons.math3.random.RandomGenerator

/**
 * At simulated [time], removes uniformly random nodes until only [survivors] are left.
 * Does nothing if the environment already has [survivors] nodes or fewer.
 *
 * @param T the type of the concentrations
 */
class KillRandomNodes<T>(
    environment: Environment<T, *>,
    private val randomGenerator: RandomGenerator,
    time: Double,
    private val survivors: Int,
) : TriggeredGlobalReaction<T>(environment, time) {
    init {
        require(survivors >= 0) { "The number of survivors must be non-negative, got $survivors" }
    }

    override fun perform() {
        environment.nodes
            .map { it to randomGenerator.nextDouble() }
            .sortedBy { it.second }
            .drop(survivors)
            .forEach { (node, _) -> environment.removeNode(node) }
    }
}
