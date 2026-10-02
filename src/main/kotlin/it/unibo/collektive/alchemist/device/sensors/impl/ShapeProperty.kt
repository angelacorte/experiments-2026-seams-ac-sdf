package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.collektive.alchemist.device.sensors.ShapeDefinition
import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.translate

/**
 * The shape the node has to form, chosen by name in the simulation file among those in `shapes.yml`
 * (see [ShapeCatalog]).
 * With global positions the shape is used where `shapes.yml` puts it ([shape]); otherwise the program moves its
 * [center] on a point of the frame it works in ([shapeAt]): the leader, or the centroid of the anchors.
 * Since the program asks for the shape at every round, the shape follows that point, e.g., when a new leader is
 * elected.
 *
 * @property node the node associated with this property.
 * @property name the name of the shape in the simulation file.
 */
class ShapeProperty<T : Any>(override val node: Node<T>, val name: String) :
    ShapeDefinition,
    NodeProperty<T> {

    private val target = shape(name)

    /** The shape called [name] in the [ShapeCatalog], where `shapes.yml` puts it. */
    val shape: SDF get() = target.sdf

    /** The center (or origin) of the [shape], as defined in `shapes.yml`. */
    val center: Position get() = target.center

    /** The [shape] moved so that its [center] lies on [point]. */
    fun shapeAt(point: Position): SDF = shape.translate(point.x - center.x, point.y - center.y)

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = ShapeProperty(node, name)
}
