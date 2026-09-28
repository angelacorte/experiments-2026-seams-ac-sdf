package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.collektive.alchemist.device.sensors.OdometrySensor
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D

/**
 * Alchemist implementation of [OdometrySensor], reading the exact displacement from the environment.
 * Nodes have no heading in the simulation, so their body frame is the one of the environment.
 *
 * @property environment the simulation environment
 * @property node the node associated with this sensor property
 */
class OdometrySensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
) : OdometrySensor,
    NodeProperty<T> {

    // Set at the first reading: the node is not in the environment yet when the property is created.
    private var start: DoubleArray? = null

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = OdometrySensorProperty(environment, node)

    override fun travelled(): Vector2D {
        val position = environment.getPosition(node).coordinates
        val origin = start ?: position.copyOf().also { start = it }
        return SpeedControl2D(position[0] - origin[0], position[1] - origin[1])
    }
}
