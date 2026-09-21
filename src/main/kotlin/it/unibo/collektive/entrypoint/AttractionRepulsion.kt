package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.stdlib.collapse.min
import it.unibo.collektive.stdlib.doubles.FieldedDoubles.plus
import kotlin.Double.Companion.POSITIVE_INFINITY

/**
 * Given the [distances] from the source, this function computes the gradient from the [source] to self.
 */
fun <ID : Comparable<ID>> Aggregate<ID>.attractionRepulsion(): Double = TODO()

/**
 * The entrypoint of the simulation running a gradient, considering the device with id 0 as the source.
 */
fun Aggregate<Int>.attractionRepulsionEntrypoint(distanceSensor: CollektiveDevice<*>): Double = with(distanceSensor) {
    TODO()
}
