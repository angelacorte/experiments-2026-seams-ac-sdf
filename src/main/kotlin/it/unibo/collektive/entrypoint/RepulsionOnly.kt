package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.currentInstant
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.formation.AdaptiveStep
import it.unibo.collektive.formation.LatticeNeighborhood
import it.unibo.collektive.formation.LocalBorder
import it.unibo.collektive.formation.RepulsionLaw
import it.unibo.collektive.formation.SpacingRule
import it.unibo.collektive.formation.latticeParameters
import it.unibo.collektive.model.Position
import it.unibo.collektive.model.minus
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.scale
import it.unibo.collektive.sdf.shape.FibonacciSpiral
import it.unibo.collektive.sdf.shape.Spiral
import it.unibo.collektive.sdf.text.toSdf
import it.unibo.collektive.stdlib.spreading.isHappeningAnywhere
import it.unibo.collektive.stdlib.time.localDeltaTime
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import it.unibo.common.limitedTo
import it.unibo.common.times
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

private val shape = FibonacciSpiral(Position(50.0, 50.0), 15.0, 6, 5.0)
    //Spiral(Position(0.0, 0.0), 10.0, 2, 10.0, 2.0).scale(3.0)

/**
 * Variant of [towardsSDFEntrypoint] that uses only repulsion between neighbors (no attraction): the SDF pulls devices
 * towards the shape, while the repulsion of the nearest neighbors ([LatticeNeighborhood], with a [RepulsionLaw])
 * spreads them out in a lattice whose spacing is fixed or adapts to the room available ([SpacingRule]), with a step
 * size that adapts to overshoots ([AdaptiveStep]). All the parameters, modes included, are read from the simulation
 * file (see `onlyRepulsion.yml`).
 */
fun Aggregate<Int>.towardsSDFRepulsionOnlyEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) =
    with(device) {
        val position = locationSensor.coordinates()
        val shape = shape//.scale(1.0 + 0.2 * sin(2 * PI * elapsed / 500.0))
        //.rotate(2 * PI * elapsed / 1000.0)
        //.translate(50.0, 50.0)
        val offsets = neighboring(position).neighbors.values.list.map { it - position }
        applyVelocity(latticeVelocity(shape, position, offsets))
    }

/**
 * The velocity of [towardsSDFRepulsionOnlyEntrypoint] for a device at [position] whose neighbors are at [offsets]
 * (`neighbor - self`), both in the frame of [shape]: however the device got them (GPS, a leader, trilateration), it
 * then forms the same lattice. The parameters are read from the simulation file (see `onlyRepulsion.yml`).
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.latticeVelocity(shape: SDF, position: Position, offsets: List<Vector2D>): SpeedControl2D {
    val parameters = device.latticeParameters()
    val border = LocalBorder.of(shape, position, parameters.gradientStep)
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
    val control = directionTowardsSDF(shape, position, parameters.gradientStep) +
        if (border.isInside) repulsion else repulsion * parameters.outsideGain
    val step = evolve(AdaptiveStep()) { it.adapt(control, parameters.step) }
    return when {
        keepsInside(shape, position, control, device.currentInstant) ->
            control.limitedTo(parameters.maxSpeed) * step.gain
        else -> control.limitedTo(parameters.maxSpeed) * step.gain
    }
}

/**
 * Whether moving with [control] for the time elapsed since the last round (up to [now]) keeps a device at [position]
 * inside [shape], if it is inside.
 */
private fun Aggregate<Int>.keepsInside(shape: SDF, position: Position, control: Vector2D, now: Instant): Boolean {
    val elapsed = localDeltaTime(now).toDouble(DurationUnit.SECONDS)
    return shape.isInside(control * elapsed + position) || !shape.isInside(position)
}
