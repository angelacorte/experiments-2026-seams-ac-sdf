package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D

/**
 * Alchemist implementation of [RelativePositionSensor], reading the exact displacement from the environment.
 *
 * @property environment the simulation environment
 * @property node the node associated with this sensor property
 */
class RelativePositionSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
) : RelativePositionSensor,
    NodeProperty<T> {

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = RelativePositionSensorProperty(environment, node)

    override fun relativeTo(neighbor: Int): Vector2D {
        val self = environment.getPosition(node).coordinates
        val other = environment.getPosition(environment.getNodeByID(neighbor)).coordinates
        return SpeedControl2D(self[0] - other[0], self[1] - other[1])
    }
}
