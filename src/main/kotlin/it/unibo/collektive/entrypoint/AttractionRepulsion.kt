package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.formation.AdaptiveStep
import it.unibo.collektive.formation.LatticeNeighborhood
import it.unibo.collektive.formation.RepulsionLaw
import it.unibo.collektive.formation.SpacingRule
import it.unibo.collektive.formation.latticeVelocity
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.minus

/**
 * Shape formation with global positions and Boids-like attraction-repulsion between neighbors, see [latticeVelocity]:
 * the SDF pulls devices towards the shape, while the nearest neighbors ([LatticeNeighborhood]) interact with a
 * [RepulsionLaw.Spring], i.e., they attract each other with a spring beyond the lattice spacing (cohesion) and repel
 * each other below it (separation). The spacing is fixed or adapts to the room available ([SpacingRule]), with a step
 * size that adapts to overshoots ([AdaptiveStep]).
 * All the parameters, modes included, are read from the simulation file (see `attractionRepulsion.yml`, where
 * `repulsion` is `spring`).
 */
fun Aggregate<Int>.attractionRepulsionEntrypoint(
    device: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    val position = locationSensor.coordinates()
    // Global positions: the shape is placed at a fixed point of the environment.
    val shape = formation.shape.placedAt(Position(parameter("shapeCenterX"), parameter("shapeCenterY")))
    val offsets = neighboring(position).neighbors.values.list.map { it - position }
    applyVelocity(latticeVelocity(shape, position, offsets))
}
