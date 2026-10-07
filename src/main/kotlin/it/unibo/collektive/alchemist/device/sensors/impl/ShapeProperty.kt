package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.collektive.alchemist.device.sensors.ShapeDefinition
import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.catalog.TargetShape
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF

/**
 * The shape the node has to form, chosen by name in the simulation file among those in `shapes.yml`
 * (see [ShapeCatalog]).
 * With global positions the shape is used where `shapes.yml` puts it ([shape]); otherwise the program moves it on a
 * point of the frame it works in (the leader, or the centroid of the anchors, see `leaderShape`).
 * The program may also ask for another shape of the catalog by name ([target], e.g., the one held by the leader).
 *
 * @property node the node associated with this property.
 * @property name the name of the shape in the simulation file.
 */
class ShapeProperty<T : Any>(override val node: Node<T>, val name: String) :
    ShapeDefinition,
    NodeProperty<T> {

    private val targets = mutableMapOf(name to shape(name))

    /** The shape called [shapeName] (by default, [name]) in the [ShapeCatalog], where `shapes.yml` puts it. */
    fun target(shapeName: String = name): TargetShape = targets.getOrPut(shapeName) { shape(shapeName) }

    /** The shape called [name] in the [ShapeCatalog], where `shapes.yml` puts it. */
    val shape: SDF get() = target().sdf

    /** The center (or origin) of the [shape], as defined in `shapes.yml`. */
    val center: Position get() = target().center

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = ShapeProperty(node, name)
}
