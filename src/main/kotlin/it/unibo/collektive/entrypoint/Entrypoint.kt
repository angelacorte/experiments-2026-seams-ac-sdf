package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.actuation.Actuation
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.coverage.latticeVelocity
import it.unibo.collektive.library.ShapeTranslation
import it.unibo.collektive.localization.Positioning

/**
 * Modular aggregate entrypoint for swarm shape formation.
 *
 * Separates the concerns of adaptive formation:
 * 1. [positioning] establishes *where* each device is, yielding its local view of the reference frame;
 * 2. [translateShape] defines *what* shape to form by placing the target SDF into that reference frame;
 * 3. [latticeVelocity] defines *how* devices coordinate to cover the shape;
 * 4. [actuate] maps the resulting control velocity into the body frame of the device and applies it.
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.swarmFormation(
    positioning: Positioning,
    translateShape: ShapeTranslation,
    actuate: Actuation = Actuation.aligned,
) {
    val frame = positioning()
    val shape = translateShape(frame)
    val control = frame.position?.let { latticeVelocity(shape, it, frame.offsets) }
    applyVelocity(actuate(frame, control))
}

/**
 * Shape formation with global positions (`positionBased.yml`): devices sense their global coordinates via
 * [locationSensor], form the [formation] shape where `shapes.yml` places it, and actuate velocities directly.
 */
fun Aggregate<Int>.positionBasedEntrypoint(
    device: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    swarmFormation(
        positioning = Positioning.global(locationSensor),
        translateShape = ShapeTranslation.global(formation),
        actuate = Actuation.aligned,
    )
}

/**
 * GPS-free shape formation with aligned local frames (`displacementBased.yml`): devices localize relative to an
 * elected leader via [sensor], translate the [formation] shape onto the leader, and actuate velocities in the aligned
 * frame while the leader stays still.
 */
fun Aggregate<Int>.displacementBasedEntrypoint(
    device: CollektiveDevice<*>,
    sensor: RelativePositionSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    swarmFormation(
        positioning = Positioning.leaderRelative(sensor),
        translateShape = ShapeTranslation.fromLeader(formation),
        actuate = Actuation.aligned,
    )
}

/**
 * Range-only shape formation (`distanceBased.yml`): three elected anchors fix a reference frame from inter-device
 * distances alone, devices trilaterate in that frame, translate the [formation] shape onto the anchors' centroid, and
 * learn to steer in their own body frame from how their commands move them.
 */
fun Aggregate<Int>.distanceBasedEntrypoint(device: CollektiveDevice<*>, formation: ShapeProperty<*>) = with(device) {
    swarmFormation(
        positioning = Positioning.anchorBased(),
        translateShape = ShapeTranslation.fromLeader(formation),
        actuate = Actuation.selfAligned(),
    )
}
