package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.localization.AnchorRole
import it.unibo.collektive.localization.FrameAlignment
import it.unibo.collektive.localization.electAnchors
import it.unibo.collektive.localization.localize
import it.unibo.collektive.model.Position
import it.unibo.collektive.model.minus
import it.unibo.collektive.sdf.impl.Star
import it.unibo.collektive.stdlib.collapse.fold
import it.unibo.common.SpeedControl2D
import it.unibo.common.times
import it.unibo.common.zeroSpeed
import org.apache.commons.math3.random.RandomGenerator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Range-only shape formation: devices sense only the distances to their neighbors (no bearing, no shared orientation).
 * Three anchors fix a frame ([electAnchors]), every device estimates its position in it ([localize]), and learns how
 * to steer in that frame from how its own commands, given in its body frame, move it among the anchors
 * ([FrameAlignment]).
 * All the parameters are read from the simulation file (see `rangeOnly.yml`).
 */
fun Aggregate<Int>.rangeOnlyEntrypoint(device: CollektiveDevice<*>) = with(device) {
    val neighborDistances = distances()
    val role = electAnchors(
        neighborDistances,
        leaderElectionBound = parameter("leaderElectionBound").toInt(),
        minAnchorsHeight = parameter("minAnchorsHeight"),
    )
    val isAnchor = role != AnchorRole.NONE
    device["leader"] = isAnchor
    val (frame, position) = localize(role, neighborDistances)
    // The star is centred on the anchors' centroid, so the anchors lie inside the shape.
    val shape = Star(
        frame.centroid,
        radius = 60.0,//parameter("starRadius"),
        pointCount = parameter("starPoints").toInt(),
        spikiness = parameter("starSpikiness"),
    )
    val repulsion = repulsionFromEstimates(
        position,
        neighborDistances,
        attractionCoefficient = parameter("attractionCoefficient"),
        desiredDistance = 60.0//parameter("desiredDistance"),
    )
    val control = position?.let {
        // Inside, the shape already keeps the swarm together: boost repulsion to spread faster.
        val repulsionGain = if (shape.isInside(it)) parameter("insideRepulsionGain") else 1.0
        directionTowardsSDF(shape, it, parameter("sdfGradientStep")) + repulsion * repulsionGain
    }
    device["control"] = control ?: zeroSpeed // The ideal command, in the anchor frame
    val lastCommand = getOrNull<SpeedControl2D>("Velocity") ?: zeroSpeed // Applied since the last round
    val commanded = evolve(zeroSpeed) { it + lastCommand }
    val alignment = evolve(FrameAlignment()) {
        it.learn(position, commanded, parameter("forgettingFactor"), parameter("maxDisplacement"))
    }
    val command = when {
        control == null -> randomGenerator.randomDirection()
        isAnchor -> zeroSpeed // The anchors are the reference frame: they stay still.
        // Explore until the alignment is reliable.
        !alignment.isConfident(parameter("minMotionEnergy"), parameter("minConfidence")) ->
            randomGenerator.randomDirection()
        // Keep some random motion, so that the alignment can still tell rotation from reflection.
        else -> alignment.toBody(control) + randomGenerator.randomDirection() * parameter("explorationNoise")
    }
    val maxSpeed = parameter("maxSpeed")
    val velocity = if (command.norm > maxSpeed) command * (maxSpeed / command.norm) else command
    applyVelocity(if (velocity.norm.isFinite()) velocity else zeroSpeed)
}

/** Reads the numeric parameter [name], set as a molecule in the simulation file. */
private fun CollektiveDevice<*>.parameter(name: String): Double =
    requireNotNull(getOrNull<Number>(name)) { "Missing parameter '$name' in the simulation file" }.toDouble()

/**
 * Repulsion from the neighbors, in the anchor frame: the direction comes from the estimated [position]s, the
 * magnitude from the measured [neighborDistances] (see [attractionRepulsionForce] for [attractionCoefficient] and
 * [desiredDistance]).
 */
private fun Aggregate<Int>.repulsionFromEstimates(
    position: Position?,
    neighborDistances: Field<Int, Double>,
    attractionCoefficient: Double,
    desiredDistance: Double,
): SpeedControl2D = neighboring(position).alignedMapValues(neighborDistances) { neighborPosition, measuredDistance ->
    when {
        position == null || neighborPosition == null -> zeroSpeed
        else -> {
            val estimatedOffset = neighborPosition - position
            attractionRepulsionForce(
                estimatedOffset * (measuredDistance / estimatedOffset.norm),
                attractionCoefficient,
                desiredDistance,
            )
        }
    }
}.neighbors.fold(zeroSpeed) { total, force -> if (force.value.norm.isFinite()) total + force.value else total }

private fun RandomGenerator.randomDirection(): SpeedControl2D =
    (2 * PI * nextDouble()).let { SpeedControl2D(cos(it), sin(it)) }
