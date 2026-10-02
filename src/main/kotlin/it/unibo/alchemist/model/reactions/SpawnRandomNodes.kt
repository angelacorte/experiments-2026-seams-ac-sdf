package it.unibo.alchemist.model.reactions

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Molecule
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Time
import org.apache.commons.math3.random.RandomGenerator

/**
 * At simulated [time], adds [count] nodes in uniformly random positions inside the rectangle
 * with bottom-left corner ([x], [y]), [width] and [height].
 * New nodes are clones of an existing node: they inherit its programs and properties,
 * but their contents are reset to the ones the template had when the simulation started,
 * so that they do not inherit the runtime state (e.g., the velocity) of the template.
 *
 * @param T the type of the concentrations
 * @param P the type of positions
 */
class SpawnRandomNodes<T, P : Position<P>>(
    environment: Environment<T, P>,
    private val randomGenerator: RandomGenerator,
    time: Double,
    private val count: Int,
    private val x: Double,
    private val y: Double,
    private val width: Double,
    private val height: Double,
) : TriggeredGlobalReaction<T>(environment, time) {
    private val typedEnvironment: Environment<T, P> = environment
    private var initialContents: Map<Molecule, T> = emptyMap()

    init {
        require(count >= 0) { "The number of nodes to spawn must be non-negative, got $count" }
    }

    override fun initializationComplete(atTime: Time, environment: Environment<T, *>) {
        // Called before the first step: the contents are still those of the deployment.
        initialContents = typedEnvironment.nodes.firstOrNull()?.contents?.toMap().orEmpty()
    }

    override fun perform() {
        val template = typedEnvironment.nodes.firstOrNull()
        checkNotNull(template) { "SpawnRandomNodes needs at least one node in the environment to clone" }
        repeat(count) {
            val clone = template.cloneNode(tau)
            clone.contents.keys.toList().forEach(clone::removeConcentration)
            initialContents.forEach(clone::setConcentration)
            val position = typedEnvironment.makePosition(
                x + randomGenerator.nextDouble() * width,
                y + randomGenerator.nextDouble() * height,
            )
            typedEnvironment.addNode(clone, position)
        }
    }
}
