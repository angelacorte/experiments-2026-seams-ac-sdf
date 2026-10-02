package it.unibo.collektive.formation

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty

/**
 * The parameters of the lattice formation, built by the simulation file as a property of each device (see
 * `repulsionOnly.yml`): the [repulsion], the [spacing] and the [step] are built there too, by `type`.
 *
 * @property repulsion how the neighbors push a device away.
 * @property maxSpeed the maximum speed of a device.
 * @property spacing how the lattice spacing evolves.
 * @property step how the step size adapts.
 */
data class LatticeParameters<T>(
    override val node: Node<T>,
    val repulsion: RepulsionLaw,
    val maxSpeed: Double,
    val spacing: SpacingRule,
    val step: StepRule,
) : NodeProperty<T> {
    override fun cloneOnNewNode(node: Node<T>) = copy(node = node)
}
