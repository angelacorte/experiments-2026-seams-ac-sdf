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
import it.unibo.collektive.sdf.text.toSdf
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import it.unibo.common.limitedTo
import it.unibo.common.times
import it.unibo.common.zeroSpeed
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

val shape = "COLLEKTIVE".toSdf(start = Position(-20.0, 30.0), height = 40.0, thickness = 4.2, spacing = 10.5,)
//val shape = Stairs(Position(0.0, 0.0), 20.0, 20.0, 5)
//val shape = FibonacciSpiral(Position(60.0, 68.0), scale = 2.0, quarterTurns = 12, thickness = 3.0)
//val shape = Spiral(Position(0.0, 0.0), spacing = 20.0, turns = 4, innerRadius = 10.0, thickness = 5.0)
//val shape = Star(Position(50.0, 50.0), 75.0, 5, 3.0)
//val shape = Circle(Position(50.0, 50.0), 40.0)
//val shape = QuestionMark(Position(100.0, 100.0), 30.0, 10.0)
//val shape = Triangle(Position(0.0, 0.0), Position(200.0, 0.0), Position(100.0, 200.0))

fun Aggregate<Int>.towardsSDFEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) = with(device) {
    val currentPosition = locationSensor.coordinates()
    val displaceToSDF: SpeedControl2D = directionTowardsSDF(
        shape,
        currentPosition,
        0.001,
    )
    val displaceAttractionRepulsion: SpeedControl2D = attractionRepulsion(currentPosition, 0.0001, 30.0)
//    val repulsionGain = if (interrogative.isInside(currentPosition)) (sqrt( -1.0 * interrogative(currentPosition)) + 1.0)  else 1.0
//    val currentControl = displaceToSDF + (displaceAttractionRepulsion * repulsionGain)
//    applyVelocity(currentControl)
//    applyVelocity(
//        if (currentControl.norm < 1.0) SpeedControl2D(currentControl.x.megaPow, currentControl.y.megaPow) else currentControl
//    )
//    val damper = 0.5
//    applyVelocity(
//        evolve(currentControl) { previousControl ->
//            currentControl * (1 - damper) + previousControl * damper
//        }
//    )
    val repulsionGain = if (shape.isInside(currentPosition)) -1.0 * shape(currentPosition)  else 1.0
//    val repulsionGain = if (shape.isInside(position)) (sqrt( -1.0 * shape(position)))  else 1.0
//    val delta: Duration = localDeltaTime(Instant.fromEpochMilliseconds((device.currentTime.toDouble() * 1000.0).toLong()))
    val control = directionTowardsSDF(shape, currentPosition, 0.001) + displaceAttractionRepulsion * repulsionGain

    val deltaMovement = control// * (delta.inWholeMilliseconds/1000.0)

    val coercedControl: Double = when {
        !star.isInside(deltaMovement + currentPosition) && star.isInside(currentPosition) -> 0.0
        else -> 1.0
    }

    val maxSpeed = 1.0
    applyVelocity(
        when {
            control.norm > maxSpeed -> control * (maxSpeed / control.norm)
            else -> control
        } * coercedControl,
    )
}

val shape = Star(Position(0.0, 0.0), 50.0, 5, 3.0)
// val shape = Triangle(Position(0.0, 0.0), Position(200.0, 0.0), Position(100.0, 200.0))


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
        val elapsed = (sharedClock(currentInstant) - Instant.DISTANT_PAST).toDouble(DurationUnit.SECONDS)
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
