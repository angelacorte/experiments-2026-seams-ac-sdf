package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.formation.*
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.sdf.ring
import it.unibo.collektive.sdf.scale
import it.unibo.collektive.sdf.shape.Star
import it.unibo.collektive.sdf.text.toSdf

private val shape = "VARDA CHE ROBA VECIO".toSdf(start = Position(-20.0, 30.0), height = 40.0, thickness = 4.2, spacing = 10.5,).scale(0.5)
//FibonacciSpiral(Position(50.0, 50.0), 15.0, 6, 5.0)
    //Spiral(Position(0.0, 0.0), 10.0, 2, 10.0, 2.0).scale(3.0)

/**
 * Shape formation with global positions and only repulsion between neighbors (no attraction), see [latticeVelocity]:
 * the SDF pulls devices towards the shape, while the repulsion of the nearest neighbors ([LatticeNeighborhood], with a [RepulsionLaw])
 * spreads them out in a lattice whose spacing is fixed or adapts to the room available ([SpacingRule]), with a step
 * size that adapts to overshoots ([AdaptiveStep]). All the parameters, modes included, are read from the simulation
 * file (see `repulsionOnly.yml`).
 */
fun Aggregate<Int>.towardsSDFRepulsionOnlyEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) =
    with(device) {
        val position = locationSensor.coordinates()
        val shape = shape//.scale(1.0 + 0.2 * sin(2 * PI * elapsed / 500.0))
        //.rotate(2 * PI * elapsed / 1000.0)
        //.translate(50.0, 50.0)
        val offsets = neighboring(position).neighbors.values.list.map { it - position }
        applyVelocity(latticeVelocity(shape, position, offsets))
    }
