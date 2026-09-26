package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.impl.Star
import it.unibo.collektive.stdlib.collapse.fold
import it.unibo.collektive.stdlib.consensus.globalElection
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.common.plus
import it.unibo.common.times
import it.unibo.common.zeroSpeed

private val origin = Position(0.0, 0.0)

/**
 * Computes the position of the device in the frame of the [source] (which sits at the origin),
 * accumulating the (dx, dy) perceived by [sensor] along the shortest path towards the source.
 */
fun Aggregate<Int>.positionRelativeTo(source: Boolean, sensor: RelativePositionSensor): Position {
    val (_, relative) = gradientCast(
        source = source,
        local = localId to zeroSpeed,
        metric = mapNeighborhood { sensor.relativeTo(it).norm },
        accumulateData = { _, _, (neighbor, position) -> localId to position + sensor.relativeTo(neighbor) },
    )
    return Position(relative.x, relative.y)
}

/**
 * GPS-free shape formation: a system-wide leader is elected and placed inside the shape (at the origin);
 * every other device estimates its position w.r.t. the leader and moves towards the shape.
 */
fun Aggregate<Int>.relativeToLeaderEntrypoint(
    device: CollektiveDevice<*>,
    sensor: RelativePositionSensor,
) = with(device) {
    val isLeader = globalElection() == localId
    device["leader"] = isLeader
    val position = positionRelativeTo(isLeader, sensor)
    val shape = Star(origin, 45.0, 5, 2.5)
    // Repulsion uses the perceived (dx, dy), not the neighbors' (possibly stale) estimated positions.
    val repulsion = mapNeighborhood { attractionRepulsionForce(sensor.relativeTo(it) * -1.0, 0.0001, 30.0) }
        .neighbors.fold(zeroSpeed) { acc, force -> acc + force.value }
    val control = directionTowardsSDF(shape, position, 0.001) + repulsion
    val maxSpeed = 1.0
    applyVelocity(
        when {
            isLeader -> zeroSpeed // The leader is the reference frame: it stays still.
            control.norm > maxSpeed -> control * (maxSpeed / control.norm)
            else -> control
        },
    )
}
