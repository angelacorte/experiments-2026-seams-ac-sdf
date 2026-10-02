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
 * Every shape is defined around an origin that lies inside it, and where that origin goes is up to the program, which
 * asks for the shape at each round with [shapeAt]: a fixed point (global positions), the leader, or the centroid of
 * the anchors. Since the origin is re-evaluated every time, the shape follows it, e.g., when a new leader is elected.
 *
 * @property node the node associated with this property.
 * @property name the name of the shape in the simulation file.
 */
class ShapeProperty<T : Any>(override val node: Node<T>, val name: String) :
    ShapeDefinition,
    NodeProperty<T> {

    /** The shape called [name] in the [ShapeCatalog], in its local frame. */
    private val localShape: SDF = shape(name)

    /** The shape with its origin on [origin], a point of the frame the node works in. */
    fun shapeAt(origin: Position): SDF = localShape.translate(origin.x, origin.y)

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = ShapeProperty(node, name)
}
