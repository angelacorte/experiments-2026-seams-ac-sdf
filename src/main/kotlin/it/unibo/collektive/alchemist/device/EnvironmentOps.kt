@file:Suppress("UndocumentedPublicFunction")

package it.unibo.collektive.alchemist.device

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D

/**
 * Relocates the current node to [newPosition] within the environment.
 */
context(device: CollektiveDevice<Euclidean2DPosition>)
fun moveNodeToPosition(newPosition: Vector2D) {
    val envPos: Position<Euclidean2DPosition> = device.environment.makePosition(newPosition.x, newPosition.y)
    device.environment.moveNodeToPosition(device.node, envPos as Euclidean2DPosition)
}

/**
 * Relocates a node identified by [nodeID] to [newPosition].
 */
context(device: CollektiveDevice<Euclidean2DPosition>)
fun moveNodeToPosition(nodeID: Int, newPosition: Vector2D) {
    val envPos: Position<Euclidean2DPosition> = device.environment.makePosition(newPosition.x, newPosition.y)
    val node = device.environment.nodes.find { it.id == nodeID }
    if (node != null) {
        device.environment.moveNodeToPosition(node, envPos as Euclidean2DPosition)
    } else {
        error("Could not find a node with ID $nodeID")
    }
}

///**
// * Builds a [Device] view for the current device state using environment variables.
// */
//context(position: LocationSensor, env: EnvironmentVariables)
//fun getRobot(): Device = position.coordinates().let {
//    val velocity = env.getOrDefault("Control", SpeedControl2D(0.0, 0.0))
//    Device(it.x, it.y, env.requiredDouble("SafeMargin"), velocity, env.requiredDouble("MaxSpeed"))
//}

private fun Node<*>.requiredDouble(molecule: String): Double {
    val concentration = getConcentration(SimpleMolecule(molecule))
    require(concentration is Number) {
        "Node $id should carry a numeric '$molecule' molecule, but its concentration is '$concentration'"
    }
    return concentration.toDouble()
}

/**
 * Applies 2the computed control [velocity][control] to the robot by moving its node inside the environment.
 *
 * Under ZOH dynamics the displacement is ∆t · u:  p_{k+1} = p_k + ∆t · u_k.
 */
context(device: CollektiveDevice<Euclidean2DPosition>)
fun applyControl(control: SpeedControl2D) {
    device["Velocity"] = control
}
