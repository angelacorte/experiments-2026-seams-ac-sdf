package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.collektive.formation.latticeVelocity
import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.shape.Triangle
import it.unibo.collektive.sdf.translate
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.time.sharedClock
import it.unibo.common.times
import it.unibo.common.zeroSpeed
import kotlin.time.Instant

// For bounded leader election
private data class Rank(val centrality: Double, val id: Int) : Comparable<Rank> {
    override fun compareTo(other: Rank) = compareValuesBy(this, other, Rank::centrality, Rank::id)
}

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
 * every other device estimates its position w.r.t. the leader and forms the lattice of [latticeVelocity] in the shape.
 */
fun Aggregate<Int>.relativeToLeaderEntrypoint(device: CollektiveDevice<*>, sensor: RelativePositionSensor) =
    with(device) {
        val leaderBasedCentrality = boundedElection(localId, 200)
        val isLeader = leaderBasedCentrality == localId
        device["leader"] = isLeader
        val position = positionRelativeTo(isLeader, sensor)
        // The shared clock counts from DISTANT_PAST, and the whole network agrees on it: 1 degree per time unit.
        val clock = sharedClock(Instant.fromEpochMilliseconds((device.currentTime.toDouble() * 1000).toLong()))
        val shape = Triangle(
            Position.origin,
            Position(200.0, 0.0),
            Position(100.0, 200.0),
        ).translate(-80.0, -80.0) // .rotate(Math.toRadians((clock - DISTANT_PAST).toDouble(DurationUnit.SECONDS)) / 3.0)

        // LetterE(origin, 100.0)
        // Star(origin, 45.0, 5, 2.5)
        // The offsets are the perceived (dx, dy), not the neighbors' (possibly stale) estimated positions.
        val offsets = mapNeighborhood { sensor.relativeTo(it) * -1.0 }.neighbors.values.list
        applyVelocity(
            when {
                isLeader -> zeroSpeed // The leader is the reference frame: it stays still.
                else -> latticeVelocity(shape, position, offsets)
            },
        )
    }
