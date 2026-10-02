package it.unibo.alchemist.model.reactions

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Molecule
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Time

/**
 * At simulated [time], adds [count] nodes, each one in the position chosen by [nextPosition].
 * New nodes are clones of an existing node: they inherit its programs and properties,
 * but their contents are reset to the ones the template had when the simulation started,
 * so that they do not inherit the runtime state (e.g., the velocity) of the template.
 *
 * @param T the type of the concentrations
 * @param P the type of positions
 */
abstract class AbstractSpawnNodes<T, P : Position<P>>(
    environment: Environment<T, P>,
    time: Double,
    private val count: Int,
) : TriggeredGlobalReaction<T>(environment, time) {
    /**
     * The environment, with the type of its positions.
     */
    protected val typedEnvironment: Environment<T, P> = environment
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
        checkNotNull(template) { "${this::class.simpleName} needs at least one node in the environment to clone" }
        // Positions of the nodes present before the spawn: the new nodes are not used as references.
        val existing = typedEnvironment.nodes.map(typedEnvironment::getPosition)
        repeat(count) {
            val clone = template.cloneNode(tau)
            clone.contents.keys.toList().forEach(clone::removeConcentration)
            initialContents.forEach(clone::setConcentration)
            typedEnvironment.addNode(clone, nextPosition(existing))
        }
    }

    /**
     * The position of the next node to spawn, given the positions of the [existing] nodes.
     */
    protected abstract fun nextPosition(existing: List<P>): P
}
