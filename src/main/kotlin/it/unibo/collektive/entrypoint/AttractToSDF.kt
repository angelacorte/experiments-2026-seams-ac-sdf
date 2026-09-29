package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.impl.Circle
import it.unibo.collektive.sdf.impl.Interrogative
import it.unibo.collektive.sdf.impl.Star
import it.unibo.collektive.sdf.impl.Triangle
import it.unibo.collektive.stdlib.spreading.isHappeningAnywhere
import it.unibo.collektive.stdlib.time.localDeltaTime
import it.unibo.common.SpeedControl2D
import it.unibo.common.times
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Instant

fun Aggregate<Int>.towardsSDFEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) = with(device) {
    val currentPosition = locationSensor.coordinates()
//    val displaceToSDF: SpeedControl2D = closestToSDF(
    val star = Star(Position(50.0, 50.0), 75.0, 5, 3.0)
    val circle = Circle(Position(50.0,50.0), 40.0)
    val interrogative = Interrogative(Position(100.0,100.0), 30.0, 10.0)
    val displaceToSDF: SpeedControl2D = directionTowardsSDF(
        interrogative,
        currentPosition,
        0.001,
    )
    val desiredDistance = evolve(2.0) {
        it.plus(0.5)
    }
    val displaceAttractionRepulsion: SpeedControl2D = attractionRepulsion(currentPosition, 0.0001, desiredDistance)
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
    val repulsionGain = if (star.isInside(currentPosition)) -1.0 * star(currentPosition)  else 1.0
//    val repulsionGain = if (shape.isInside(position)) (sqrt( -1.0 * shape(position)))  else 1.0
    val delta: Duration = localDeltaTime(Instant.fromEpochMilliseconds((device.currentTime.toDouble() * 1000.0).toLong()))
    val control = directionTowardsSDF(star, currentPosition, 0.001) + displaceAttractionRepulsion * repulsionGain

    val deltaMovement = control * (delta.inWholeMilliseconds/1000.0)

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

//val shape = Star(Position(50.0, 50.0), 75.0, 5, 3.0)
val shape = Triangle(Position(0.0, 0.0), Position(200.0, 0.0), Position(100.0, 200.0))

/**
 * Variant of [towardsSDFEntrypoint] that uses only repulsion between neighbors (no attraction):
 * the SDF pulls devices towards the shape, while the neighbor repulsion spreads them out.
 */
fun Aggregate<Int>.towardsSDFRepulsionOnlyEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) =
    with(device) {
        val currentPosition = locationSensor.coordinates()
        val isSomeoneOutsideSDF = isHappeningAnywhere { !shape.isInside(currentPosition) }
        val desiredDistance = evolve(2.0) {
            it.plus(0.5).coerceIn(1.0, 100.0).also { device["desiredDistance"] = it }
        }
        val displaceRepulsion: SpeedControl2D = repulsion(currentPosition, 0.0001, desiredDistance)
        val repulsionGain = if (shape.isInside(currentPosition)) (sqrt(-1.0 * shape(currentPosition))) else 1.0
        val delta: Duration =
            localDeltaTime(Instant.fromEpochMilliseconds((device.currentTime.toDouble() * 1000.0).toLong()))
        val control = directionTowardsSDF(shape, currentPosition, 0.001) + displaceRepulsion * repulsionGain
        val deltaMovement = control * (delta.inWholeMilliseconds / 1000.0)
        val coercedControl: Double = when {
            !shape.isInside(deltaMovement + currentPosition) && shape.isInside(currentPosition) -> 0.0
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

private val Double.megaPow: Double get() = pow(3)
