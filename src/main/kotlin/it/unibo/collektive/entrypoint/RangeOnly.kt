package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.formation.latticeVelocity
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.localization.AnchorRole
import it.unibo.collektive.localization.FrameAlignment
import it.unibo.collektive.localization.electAnchors
import it.unibo.collektive.localization.localize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.apache.commons.math3.random.RandomGenerator

/**
 * Range-only shape formation: devices sense only the distances to their neighbors (no bearing, no shared orientation).
 * Three anchors fix a frame ([electAnchors]), every device estimates its position in it ([localize]), forms the
 * lattice of [latticeVelocity] in that frame, and learns how to steer in it from how its own commands, given in its
 * body frame, move it among the anchors ([FrameAlignment]).
 * All the parameters are read from the simulation file (see `rangeOnly.yml`).
 */
fun Aggregate<Int>.rangeOnlyEntrypoint(device: CollektiveDevice<*>, formation: ShapeProperty<*>) = with(device) {
    val neighborDistances = distances()
    val role = electAnchors(
        neighborDistances,
        leaderElectionBound = parameter("leaderElectionBound").toInt(),
        minAnchorsHeight = parameter("minAnchorsHeight"),
    )
    val isAnchor = role != AnchorRole.NONE
    device["leader"] = isAnchor
    val (frame, position) = localize(role, neighborDistances)
    // The center of the shape is on the anchors' centroid: it follows the anchors when they change.
    val shape = formation.shapeAt(frame.centroid)
    val offsets = offsetsFromEstimates(position, neighborDistances)
    val control = position?.let { latticeVelocity(shape, it, offsets) }
    device["control"] = control ?: zeroSpeed // The ideal command, in the anchor frame
    val lastCommand = getOrNull<SpeedControl2D>("Velocity") ?: zeroSpeed // Applied since the last round
    val commanded = evolve(zeroSpeed) { it + lastCommand }
    val alignment = evolve(FrameAlignment()) {
        it.learn(position, commanded, parameter("forgettingFactor"), parameter("maxDisplacement"))
    }
    val command = when {
        control == null -> randomGenerator.randomDirection() * parameter("explorationSpeed")
        isAnchor -> zeroSpeed // The anchors are the reference frame: they stay still.
        // Explore until the alignment is reliable.
        !alignment.isConfident(parameter("minMotionEnergy"), parameter("minConfidence")) ->
            randomGenerator.randomDirection() * parameter("explorationSpeed")
        // Keep some random motion, so that the alignment can still tell rotation from reflection.
        else -> alignment.toBody(control) // + randomGenerator.randomDirection() * parameter("explorationNoise")
    }
    val maxSpeed = parameter("maxSpeed")
    val velocity = if (command.norm > maxSpeed) command * (maxSpeed / command.norm) else command
    applyVelocity(if (velocity.norm.isFinite()) velocity else zeroSpeed)
}

/**
 * The offsets of the neighbors (`neighbor - self`), in the anchor frame: the direction comes from the estimated
 * [position]s, the length from the measured [neighborDistances]. Empty until the device is localized.
 */
private fun Aggregate<Int>.offsetsFromEstimates(
    position: Position?,
    neighborDistances: Field<Int, Double>,
): List<Vector2D> = neighboring(position).alignedMapValues(neighborDistances) { neighborPosition, measuredDistance ->
    when {
        position == null || neighborPosition == null -> null
        else -> (neighborPosition - position).let { it * (measuredDistance / it.norm) }
    }
}.neighbors.values.list.filterNotNull().filter { it.norm.isFinite() }

private fun RandomGenerator.randomDirection(): SpeedControl2D =
    (2 * PI * nextDouble()).let { SpeedControl2D(cos(it), sin(it)) }
