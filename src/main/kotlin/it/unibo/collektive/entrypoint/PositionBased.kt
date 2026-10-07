package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.coverage.AdaptiveStep
import it.unibo.collektive.coverage.RepulsionLaw
import it.unibo.collektive.coverage.latticeVelocity
import it.unibo.collektive.geometry.minus

/**
 * Shape formation with global positions, see [latticeVelocity]: the SDF pulls devices towards the shape, while their
 * neighbors spread them out ([RepulsionLaw]), with a step size that adapts to overshoots ([AdaptiveStep]).
 * All the parameters are read from the simulation file (see `positionBased.yml`).
 */
fun Aggregate<Int>.positionBasedEntrypoint(
    device: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    val position = locationSensor.coordinates()
    // Global positions: the shape lies where shapes.yml puts it.
    val offsets = neighboring(position).neighbors.values.list.map { it - position }
    applyVelocity(latticeVelocity(formation.shape, position, offsets))
}
