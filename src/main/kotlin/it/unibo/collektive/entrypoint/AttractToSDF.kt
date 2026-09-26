package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.impl.Circle
import it.unibo.collektive.sdf.impl.Interrogative
import it.unibo.collektive.sdf.impl.Star
import it.unibo.common.SpeedControl2D
import it.unibo.common.Vector2D
import it.unibo.common.plus
import it.unibo.common.times
import kotlin.math.pow

fun Aggregate<Int>.towardsSDFEntrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) = with(device) {
    val currentPosition = locationSensor.coordinates()
//    val displaceToSDF: SpeedControl2D = closestToSDF(
    val star = Star(Position(50.0, 50.0), 45.0, 5, 2.5)
    val circle = Circle(Position(50.0,50.0), 40.0)
    val interrogative = Interrogative(Position(100.0,100.0), 30.0, 10.0)
    val displaceToSDF: SpeedControl2D = directionTowardsSDF(
        interrogative,
        currentPosition,
        0.001,
    )
    val displaceAttractionRepulsion: SpeedControl2D = attractionRepulsion(currentPosition, 0.0001, 30.0,)
    val currentControl = displaceToSDF + displaceAttractionRepulsion
    applyVelocity(currentControl)
//    applyVelocity(
//        if (currentControl.norm < 1.0) SpeedControl2D(currentControl.x.megaPow, currentControl.y.megaPow) else currentControl
//    )
//    val damper = 0.5
//    applyVelocity(
//        evolve(currentControl) { previousControl ->
//            currentControl * (1 - damper) + previousControl * damper
//        }
//    )
}

private val Double.megaPow: Double get() = pow(3)
