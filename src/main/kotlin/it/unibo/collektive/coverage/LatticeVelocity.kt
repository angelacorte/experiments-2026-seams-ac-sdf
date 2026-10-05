package it.unibo.collektive.coverage

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Node.Companion.asProperty
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.dot
import it.unibo.collektive.geometry.limitedTo
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.sdf.SDF
import kotlin.math.max

// The largest push of the neighbors out of the shape, as a fraction of the pull of the shape: enough to keep the
// devices from bumping into each other on their way, never more than the pull, so that they always come in
private const val OUTSIDE_PUSH = 0.5

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
    val repulsion = parameters.repulsion.control(neighborhood, spacing, shape, position)
    val control = when {
        border.isInside -> repulsion // The mirror images, or the shape clipping the cells, keep devices off the border
        else -> { // The SDF pulls the device in; the neighbors push it aside, less than that, and never back out
            val aside = repulsion - border.outward * max(0.0, repulsion dot border.outward)
            border.towardsShape + aside.limitedTo(OUTSIDE_PUSH)
        }
    }
    val step = evolve(AdaptiveStep()) { it.adapt(control, parameters.step) }
    return control.limitedTo(parameters.maxSpeed) * step.gain
}
