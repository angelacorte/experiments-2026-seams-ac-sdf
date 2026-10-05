package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.formation.AdaptiveStep
import it.unibo.collektive.formation.LatticeNeighborhood
import it.unibo.collektive.formation.RepulsionLaw
import it.unibo.collektive.formation.SpacingRule
import it.unibo.collektive.formation.latticeVelocity
import it.unibo.collektive.geometry.minus

/**
 * Shape formation with global positions and only repulsion between neighbors (no attraction), see [latticeVelocity]:
 * the SDF pulls devices towards the shape, while the repulsion of the nearest neighbors ([LatticeNeighborhood],
 * with a [RepulsionLaw]) spreads them out in a lattice whose spacing is fixed or adapts to the room available
 * ([SpacingRule]), with a step size that adapts to overshoots ([AdaptiveStep]).
 * All the parameters, modes included, are read from the simulation file (see `repulsionOnly.yml`).
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
