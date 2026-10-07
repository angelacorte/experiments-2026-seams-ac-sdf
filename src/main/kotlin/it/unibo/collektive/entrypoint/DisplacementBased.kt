package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.coverage.latticeVelocity
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.library.leaderShape
import it.unibo.collektive.localization.positionRelativeTo
import it.unibo.collektive.stdlib.consensus.boundedElection

/**
 * GPS-free shape formation: a system-wide leader is elected and placed inside the shape (at the origin, see
 * [leaderShape]);
 * every other device estimates its position w.r.t. the leader and forms the lattice of [latticeVelocity] in the shape.
 */
fun Aggregate<Int>.displacementBasedEntrypoint(
    device: CollektiveDevice<*>,
    sensor: RelativePositionSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    val leaderBasedCentrality = boundedElection(-localId, 50)
    val isLeader = leaderBasedCentrality == localId
    device["leader"] = isLeader
    val position = positionRelativeTo(isLeader, sensor)
    val shape = leaderShape(device, isLeader, formation, Position.origin)
    val offsets = mapNeighborhood { sensor.relativeTo(it) * -1.0 }.neighbors.values.list
    applyVelocity(
        when {
            isLeader -> zeroSpeed // The leader is the reference frame: it stays still.
            else -> latticeVelocity(shape, position, offsets)
        },
    )
}
