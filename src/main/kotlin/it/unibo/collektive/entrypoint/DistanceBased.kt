package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.coverage.latticeVelocity
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.limitedTo
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.randomDirection
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.library.leaderShape
import it.unibo.collektive.localization.AnchorRole
import it.unibo.collektive.localization.FrameAlignment
import it.unibo.collektive.localization.electAnchors
import it.unibo.collektive.localization.localize
import it.unibo.collektive.localization.offsetsFromEstimates

/**
 * Range-only shape formation: devices sense only the distances to their neighbors (no bearing, no shared orientation).
 * Three anchors fix a frame ([electAnchors]), every device estimates its position in it ([localize]), forms the
 * lattice of [latticeVelocity] in that frame, and learns how to steer in it from how its own commands, given in its
 * body frame, move it among the anchors ([FrameAlignment]). The system stays still until the anchors are chosen.
 * All the parameters are read from the simulation file (see `distanceBased.yml`).
 */
fun Aggregate<Int>.distanceBasedEntrypoint(device: CollektiveDevice<*>, formation: ShapeProperty<*>) = with(device) {
    val neighborDistances = distances()
    val role = electAnchors(
        neighborDistances,
        leaderElectionBound = parameter("leaderElectionBound").toInt(),
        minAnchorsHeight = parameter("minAnchorsHeight"),
        anchorsSize = parameter("anchorsSize"),
        anchorPatience = parameter("anchorPatience").toInt(),
    )
    val isAnchor = role != AnchorRole.NONE
    device["leader"] = isAnchor
    device["anchor"] = role.name // Tells the anchors apart, for the metrics: they need not be neighbors
    val (frame, position) = localize(role, neighborDistances)
    val shape = leaderShape(device, role == AnchorRole.ANCHOR_1, formation, frame.centroid)
    val offsets = offsetsFromEstimates(position, neighborDistances)
    val control = position?.let { latticeVelocity(shape, it, offsets) }
    device["control"] = control ?: zeroSpeed // The ideal command, in the anchor frame
    val lastCommand = getOrNull<SpeedControl2D>("Velocity") ?: zeroSpeed // Applied since the last round
    val commanded = evolve(zeroSpeed) { it + lastCommand }
    val alignment = evolve(FrameAlignment()) {
        it.learn(position, commanded, parameter("forgettingFactor"), parameter("maxDisplacement"))
    }
    val steering = evolve(false) {
        it || alignment.isConfident(parameter("minMotionEnergy"), parameter("minConfidence"))
    }
    val probe = evolve(zeroSpeed to false) { (direction, out) ->
        if (out) direction * -1.0 to false else randomGenerator.randomDirection() to true
    }.first * parameter("explorationSpeed")
    val unlocalized = evolve(0) { if (control == null) it + 1 else 0 } // Rounds since the device lost the frame
    // Run and tumble towards the nearest neighbor: the direction is kept while the distance shrinks, else drawn anew.
    val command = when {
        control == null -> if (unlocalized > parameter("maxFrameWait")) probe else zeroSpeed
        isAnchor -> zeroSpeed // The anchors are the reference frame: they stay still.
        !steering -> probe
        else -> alignment.toBody(control) // + randomGenerator.randomDirection() * parameter("explorationNoise")
    }
    val velocity = command.limitedTo(parameter("maxSpeed"))
    applyVelocity(if (velocity.norm.isFinite()) velocity else zeroSpeed)
}
