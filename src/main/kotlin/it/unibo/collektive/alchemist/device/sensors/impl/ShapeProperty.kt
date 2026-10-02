package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.collektive.alchemist.device.sensors.ShapeDefinition
import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.sdf.SDF

/**
 * The [shape] the node has to form, chosen by name in the simulation file among those in `shapes.yml`
 * (see [ShapeCatalog]).
 * Where the shape lies is up to the program.
 *
 * @property node the node associated with this property.
 * @property name the name of the shape in the simulation file.
 */
class ShapeProperty<T : Any>(override val node: Node<T>, val name: String) :
    ShapeDefinition,
    NodeProperty<T> {

    /** The shape called [name] in the [ShapeCatalog], in its local frame. */
    val shape: SDF = shape(name)

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = ShapeProperty(node, name)
}
