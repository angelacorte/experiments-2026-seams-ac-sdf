package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.stdlib.spreading.isHappeningAnywhere
import it.unibo.collektive.stdlib.time.localDeltaTime
import it.unibo.common.SpeedControl2D
import it.unibo.common.times
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Variant of [towardsSDFEntrypoint] that uses only repulsion between neighbors (no attraction):
 * the SDF pulls devices towards the shape, while the neighbor repulsion spreads them out.
 */
fun Aggregate<Int>.towardsSDFRepulsionOnlyEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) =
    with(device) {
        val currentPosition = locationSensor.coordinates()
        val isSomeoneOutsideSDF = isHappeningAnywhere { shape.isOutside(currentPosition) }
//        val desiredDistance = evolve(2.0) {
//            it.plus(0.5).coerceIn(1.0, 100.0).also { device["desiredDistance"] = it }
//        }
        val displaceRepulsion: SpeedControl2D = repulsion(currentPosition, 0.0001, 60.0)
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
