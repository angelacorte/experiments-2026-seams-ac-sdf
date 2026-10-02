package it.unibo.collektive.formation

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Node.Companion.asProperty
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.limitedTo
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.sdf.SDF

private const val OUTSIDE_GAIN = 0.1 // Scale of the repulsion outside the shape, where the SDF pulls the devices in

/**
 * The lattice velocity for a device at [position] whose neighbors are at [offsets] (`neighbor - self`), both in the
 * frame of [shape]: however the device got them (GPS, a leader, trilateration), it then forms the same lattice.
 * The parameters are the [LatticeParameters] of the device, built by the simulation file (see `repulsionOnly.yml`).
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.latticeVelocity(shape: SDF, position: Position, offsets: List<Vector2D>): SpeedControl2D {
    val parameters: LatticeParameters<Any?> = device.node.asProperty()
    val border = LocalBorder.of(shape, position)
    device["distanceToSDF"] = border.distance
    val neighborhood = LatticeNeighborhood(offsets, border)
    val spacing = parameters.spacing.current(shape, neighborhood)
    device["desiredDistance"] = spacing
    val repulsion = neighborhood.repulsion(spacing, parameters.repulsion)
    // Weak outside (the SDF pulls devices in); inside, the mirror images keep devices off the border.
    val control = border.towardsShape +
        if (border.isInside) repulsion else repulsion * OUTSIDE_GAIN
    val step = evolve(AdaptiveStep()) { it.adapt(control, parameters.step) }
    return control.limitedTo(parameters.maxSpeed) * step.gain
}
