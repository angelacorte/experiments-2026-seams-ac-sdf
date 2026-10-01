package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.collektive.alchemist.device.sensors.ShapeDefinition
import it.unibo.collektive.entrypoint.ShapeCatalog

/**
 * The [shape] the node has to form, chosen by name in the simulation file (e.g., `star`, see [it.unibo.collektive.entrypoint.ShapeCatalog]).
 * Where the shape lies is up to the program.
 *
 * @property node the node associated with this property
 */
class ShapeProperty<T : Any>(override val node: Node<T>, val name: String) : ShapeDefinition, NodeProperty<T> {

    val shape: ShapeCatalog = shape(name)

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = ShapeProperty(node, name)
}
