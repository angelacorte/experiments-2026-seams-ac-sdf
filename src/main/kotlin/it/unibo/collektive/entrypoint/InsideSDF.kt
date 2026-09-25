package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.model.Position
import it.unibo.collektive.model.minus
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.impl.Circle
import it.unibo.collektive.stdlib.collapse.fold
import it.unibo.collektive.stdlib.collapse.reduce
import it.unibo.collektive.stdlib.pairs.FieldedPairs.first
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import it.unibo.common.times
import it.unibo.common.zeroSpeed
import kotlin.math.hypot
import kotlin.math.sign

fun Aggregate<Int>.insideSDFEntrypoint(
    device: CollektiveDevice<*>,
    locationSensor: LocationSensor,
) = with(device) {
    val displacement = closestToSDF(
        Circle(
            Position(50.0,50.0),
            50.0,
        ),
        locationSensor.coordinates(),
    )
    val length = hypot(displacement.x, displacement.y)
    val directionX = if (length > 0.0) displacement.x / length else 0.0
    val directionY = if (length > 0.0) displacement.y / length else 0.0
    // Speed is chosen independently of direction.
    val speed = 1.0
    applyVelocity(SpeedControl2D(directionX * speed, directionY * speed),)
}

context(device: CollektiveDevice<*>)
fun <ID: Comparable<ID>> Aggregate<ID>.closestToSDF(
    sdf: SDF,
    currentPosition: Position,
    speed: Double = 0.01,
): SpeedControl2D {
    val distanceToSDF: Double = sdf(currentPosition)
    device["distanceToSDF"] = distanceToSDF
    return neighboring(currentPosition to distanceToSDF).mapValues { (position, magnitude) ->
        (currentPosition - position) * sign(magnitude)
    }.neighbors.values.reduce { d: Vector2D, d2 -> d + d2 }?.times(speed) ?: zeroSpeed
//    val closest = share(currentPosition to distanceToSDF) { positions: Field<ID, Pair<Position, Double>> ->
//        currentPosition to distanceToSDF
//        positions
////        positions.all.fold(currentPosition to distanceToSDF) { accumulator, next ->
////            when {
////                accumulator.second > next.value.second -> next.value
////                else -> accumulator
////            }
////        }
//    }
//    val vector = closest.first - currentPosition
//    return SpeedControl2D(vector.x, vector.y)
}

fun gradientToSDF(sdf: SDF, currentPosition: Position, epsilon: Double): SpeedControl2D {
    val dx = sdf(Position(currentPosition.x + epsilon, currentPosition.y)) - sdf(Position(currentPosition.x - epsilon, currentPosition.y))
    val dy = sdf(Position(currentPosition.x, currentPosition.y + epsilon)) - sdf(Position(currentPosition.x, currentPosition.y - epsilon))
    return SpeedControl2D(dx, dy)
}

fun directionTowardsSDF(
    sdf: SDF,
    currentPosition: Position,
    epsilon: Double,
): SpeedControl2D {
    val distance = sdf(currentPosition)
    val gradient = gradientToSDF(sdf, currentPosition, epsilon)
    val magnitude = hypot(gradient.x, gradient.y)
    return when {
        distance <= 0.0 -> zeroSpeed
        magnitude > 0.0 -> SpeedControl2D(-gradient.x / magnitude, -gradient.y / magnitude)
        else -> zeroSpeed
    }
}
