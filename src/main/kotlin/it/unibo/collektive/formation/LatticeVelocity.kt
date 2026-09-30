package it.unibo.collektive.formation

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import it.unibo.common.limitedTo
import it.unibo.common.times

/**
 * The lattice velocity for a device at [position] whose neighbors are at [offsets] (`neighbor - self`), both in the
 * frame of [shape]: however the device got them (GPS, a leader, trilateration), it then forms the same lattice.
 * The parameters are read from the simulation file (see `repulsionOnly.yml`).
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.latticeVelocity(shape: SDF, position: Position, offsets: List<Vector2D>): SpeedControl2D {
    val parameters = device.latticeParameters()
    val border = LocalBorder.of(shape, position, parameters.gradientStep)
    device["distanceToSDF"] = border.distance
    val neighborhood = LatticeNeighborhood(
        neighbors = offsets,
        border = border,
        ringSize = parameters.ringSize,
    )
    val spacing = evolve(parameters.spacing.initial) {
        parameters.spacing.next(it, neighborhood)
    }
    device["desiredDistance"] = spacing
    val repulsion = neighborhood.repulsion(spacing, parameters.repulsion)
    // Weak outside (the SDF pulls devices in); inside, the mirror images keep devices off the border.
    val control = border.towardsShape +
        if (border.isInside) repulsion else repulsion * parameters.outsideGain
    val step = evolve(AdaptiveStep()) { it.adapt(control, parameters.step) }
    return control.limitedTo(parameters.maxSpeed) * step.gain
}

