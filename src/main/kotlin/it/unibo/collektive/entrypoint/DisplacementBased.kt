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
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.deepestPointFrom
import it.unibo.collektive.sdf.translate
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.spreading.hopGradientCast

// For bounded leader election: the device with the most neighbors wins, the id breaks ties
private data class Rank(val neighbors: Int, val id: Int) : Comparable<Rank> {
    override fun compareTo(other: Rank) = compareValuesBy(this, other, Rank::neighbors, Rank::id)
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
 * The shape held by a device: its [name] in the catalog, and the point [inside] it (where `shapes.yml` puts it) that
 * the devices place where the shape belongs (the leader, or the centroid of the anchors).
 */
data class HeldShape(val name: String, val inside: Position)

/**
 * The shape held by the [leader], spread to every device by its nearest leader, moved so that the point inside it
 * that the leader found lies on [point] (the leader, or the centroid of the anchors, in the frame of the device).
 * The leader takes its shape where `shapes.yml` puts it, and finds the point deep inside it nearest to its own [point]
 * (see [deepestPointFrom]): down to the border, then inward along the gradient of the SDF.
 * The shape is the `shape` molecule of the leader (the name of the [formation] when missing), so that changing the
 * molecule on the leader reshapes the swarm.
 * Every device stores the shape it perceives in its `shape` molecule, and the point inside it in `shapeCenter`, at
 * every round: a newly elected leader keeps the current shape, and the metrics measure the shape the devices actually
 * form.
 */
fun Aggregate<Int>.leaderShape(
    device: CollektiveDevice<*>,
    leader: Boolean,
    formation: ShapeProperty<*>,
    point: Position,
): SDF {
    val name = device.getOrNull<String>("shape") ?: formation.name
    // Only the leader looks for the point inside, the others take the one it spreads.
    val local = HeldShape(name, if (leader) formation.target(name).sdf.deepestPointFrom(point) else point)
    val (held, inside) = hopGradientCast(leader, local)
    device["shape"] = held
    device["shapeCenter"] = inside
    return formation.target(held).sdf.translate(point.x - inside.x, point.y - inside.y)
}

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
