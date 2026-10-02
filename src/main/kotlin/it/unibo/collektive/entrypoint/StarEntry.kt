package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.formation.latticeVelocity
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.sdf.scale
import it.unibo.collektive.sdf.shape.Star

internal val center = Position(50.0, 50.0)
internal val star = Star(center, radius = 45.0, pointCount = 5)

/**
 * As [repulsionOnlyEntrypoint], on a star that all the devices must enter from one side (see `starEntry.yml`).
 */
fun Aggregate<Int>.starEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) = with(device) {
    val position = locationSensor.coordinates()
    val offsets = neighboring(position).neighbors.values.list.map { it - position }
    applyVelocity(latticeVelocity(star.scale(parameter("shapeScale"), center), position, offsets))
}
