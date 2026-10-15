package it.unibo.collektive.localization

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.stdlib.consensus.boundedElection

private const val DEFAULT_LEADER_BOUND = 50

/**
 * The local view of the reference frame in which a device evaluates the target shape and coordinates with its
 * neighbors.
 *
 * @property position the estimated position of the device in the frame, or `null` until the frame is known.
 * @property offsets the offsets of the neighbors (`neighbor - self`) in the frame.
 * @property center the reference point of the frame on which a translated shape is placed.
 * @property isLeader whether this device is the leader that holds and spreads the shape.
 * @property isReference whether this device fixes the frame (e.g., the leader or an anchor) and must stay still.
 */
data class ReferenceFrame(
    val position: Position?,
    val offsets: List<Vector2D>,
    val center: Position = Position.origin,
    val isLeader: Boolean = false,
    val isReference: Boolean = isLeader,
)

/**
 * Strategy that establishes the [ReferenceFrame] in which a device evaluates the shape and perceives its neighbors.
 */
fun interface Positioning {
    /** Computes the [ReferenceFrame] of the device for the current round. */
    fun Aggregate<Int>.locate(): ReferenceFrame

    /** Invokes [locate] in the current [aggregate] context. */
    context(aggregate: Aggregate<Int>)
    operator fun invoke(): ReferenceFrame = aggregate.locate()

    /** Built-in [Positioning] strategies for the three spatial perception regimes. */
    companion object {
        /**
         * Position-based (global) perception: each device directly senses its global coordinates via [locationSensor]
         * and exchanges them with its neighbors (see [globalPositioning]).
         */
        fun global(locationSensor: LocationSensor): Positioning = Positioning {
            globalPositioning(locationSensor)
        }

        /**
         * Displacement-based (leader-relative) perception: a leader is elected within [leaderElectionBound] hops, and
         * every device estimates its position relative to the leader via [sensor] (see [leaderPositioning]).
         */
        context(device: CollektiveDevice<*>)
        fun leaderRelative(
            sensor: RelativePositionSensor,
            leaderElectionBound: Int = DEFAULT_LEADER_BOUND,
        ): Positioning = Positioning {
            leaderPositioning(sensor, leaderElectionBound)
        }

        /**
         * Distance-based (range-only) perception: three elected anchors fix a coordinate frame from inter-device
         * distances alone, and every device trilaterates its position in that frame (see [anchorPositioning]).
         */
        context(device: CollektiveDevice<*>)
        fun anchorBased(): Positioning = Positioning {
            anchorPositioning()
        }
    }
}

/**
 * Global positioning: the device reads its absolute coordinates from [locationSensor] and exchanges them with its
 * neighbors to compute their relative offsets.
 */
fun Aggregate<Int>.globalPositioning(locationSensor: LocationSensor): ReferenceFrame {
    val position = locationSensor.coordinates()
    val offsets = neighboring(position).neighbors.values.list.map { it - position }
    return ReferenceFrame(position, offsets)
}

/**
 * Leader-relative positioning: elects a system-wide leader within [leaderElectionBound] hops and computes the position
 * of every device in the frame centered on the leader by accumulating the displacements sensed by [sensor].
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.leaderPositioning(
    sensor: RelativePositionSensor,
    leaderElectionBound: Int = DEFAULT_LEADER_BOUND,
): ReferenceFrame {
    val isLeader = boundedElection(-localId, leaderElectionBound) == localId
    device["leader"] = isLeader
    val position = positionRelativeTo(isLeader, sensor)
    val offsets = mapNeighborhood { sensor.relativeTo(it) * -1.0 }.neighbors.values.list
    return ReferenceFrame(position, offsets, isLeader = isLeader)
}

/**
 * Range-only anchor-based positioning: elects three anchors ([electAnchors]) from neighbor distances alone,
 * trilaterates the device position in the frame they fix ([localize]), and estimates neighbor offsets
 * ([offsetsFromEstimates]).
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.anchorPositioning(): ReferenceFrame = with(device) {
    val neighborDistances = distances()
    val role = electAnchors(
        neighborDistances,
        leaderElectionBound = parameter("leaderElectionBound").toInt(),
        minAnchorsHeight = parameter("minAnchorsHeight"),
        anchorsSize = parameter("anchorsSize"),
        anchorPatience = parameter("anchorPatience").toInt(),
    )
    val isAnchor = role != AnchorRole.NONE
    device["leader"] = isAnchor
    device["anchor"] = role.name // Tells the anchors apart, for the metrics: they need not be neighbors
    val (frame, position) = localize(role, neighborDistances)
    val offsets = offsetsFromEstimates(position, neighborDistances)
    ReferenceFrame(
        position = position,
        offsets = offsets,
        center = frame.centroid,
        isLeader = role == AnchorRole.ANCHOR_1,
        isReference = isAnchor,
    )
}
