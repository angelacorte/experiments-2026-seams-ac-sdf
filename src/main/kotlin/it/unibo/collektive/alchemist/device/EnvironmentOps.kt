@file:Suppress("UndocumentedPublicFunction")

package it.unibo.collektive.alchemist.device

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import kotlin.time.Instant

private const val MILLISECONDS_PER_SECOND = 1000.0

/** The current simulation time of this device, as an [Instant] with millisecond precision. */
val CollektiveDevice<*>.currentInstant: Instant
    get() = Instant.fromEpochMilliseconds((currentTime.toDouble() * MILLISECONDS_PER_SECOND).toLong())

/**
 * Applies 2the computed control [velocity][velocity] to the robot by moving its node inside the environment.
 */
context(device: CollektiveDevice<*>)
fun applyVelocity(velocity: SpeedControl2D) {
    device["Velocity"] = velocity
}
