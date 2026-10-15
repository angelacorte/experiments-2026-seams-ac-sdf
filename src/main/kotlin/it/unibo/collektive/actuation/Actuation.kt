package it.unibo.collektive.actuation

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.limitedTo
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.randomDirection
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.localization.FrameAlignment
import it.unibo.collektive.localization.ReferenceFrame

/**
 * Strategy that maps the desired control velocity in the [ReferenceFrame] into the velocity command in the body frame
 * of the device.
 */
fun interface Actuation {
    /** Computes the velocity command in the body frame from the [frame] and the desired [control] velocity. */
    fun Aggregate<Int>.actuate(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D

    /** Invokes [actuate] in the current [aggregate] context. */
    context(aggregate: Aggregate<Int>)
    operator fun invoke(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D =
        aggregate.actuate(frame, control)

    /** Built-in [Actuation] strategies. */
    companion object {
        /**
         * Direct actuation for devices whose local frame is aligned with the [ReferenceFrame]: reference devices fixing
         * the frame ([ReferenceFrame.isReference]) stay still, while all other devices apply [control] directly
         * (see [alignedActuation]).
         */
        val aligned: Actuation = Actuation { frame, control ->
            alignedActuation(frame, control)
        }

        /**
         * Self-aligned actuation for range-only devices operating in an unknown local body frame: each device learns
         * the orthogonal transformation between its body frame and the [ReferenceFrame] from how its own commands move
         * it ([FrameAlignment]), probing the environment with run-and-tumble steps until confident
         * (see [selfAlignedActuation]).
         */
        context(device: CollektiveDevice<*>)
        fun selfAligned(): Actuation = Actuation { frame, control ->
            selfAlignedActuation(frame, control)
        }
    }
}

/**
 * Actuation when the local frame of the device is aligned with [frame]: reference devices
 * ([ReferenceFrame.isReference]) stay still (`zeroSpeed`), while all other devices track [control] directly.
 */
fun alignedActuation(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D =
    if (frame.isReference || control == null) zeroSpeed else control

/**
 * Actuation when the local body frame of the device has an unknown orientation w.r.t. [frame]: the device learns the
 * alignment from its own motion ([FrameAlignment]) and explores with a run-and-tumble probe until confident.
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.selfAlignedActuation(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D {
    device["control"] = control ?: zeroSpeed // The ideal command, in the anchor frame
    val lastCommand = device.getOrNull<SpeedControl2D>("Velocity") ?: zeroSpeed // Applied since the last round
    val commanded = evolve(zeroSpeed) { it + lastCommand }
    val alignment = evolve(FrameAlignment()) {
        it.learn(frame.position, commanded, device.parameter("forgettingFactor"), device.parameter("maxDisplacement"))
    }
    val steering = evolve(false) {
        it || alignment.isConfident(device.parameter("minMotionEnergy"), device.parameter("minConfidence"))
    }
    val probe = evolve(zeroSpeed to false) { (direction, out) ->
        if (out) direction * -1.0 to false else device.randomGenerator.randomDirection() to true
    }.first * device.parameter("explorationSpeed")
    val unlocalized = evolve(0) { if (control == null) it + 1 else 0 } // Rounds since the device lost the frame
    // Run and tumble: the direction is kept for one step and reversed on the next, else drawn anew.
    val command = when {
        control == null -> if (unlocalized > device.parameter("maxFrameWait")) probe else zeroSpeed
        frame.isReference -> zeroSpeed // The anchors are the reference frame: they stay still.
        !steering -> probe
        else -> alignment.toBody(control)
    }
    val velocity = command.limitedTo(device.parameter("maxSpeed"))
    return if (velocity.norm.isFinite()) velocity else zeroSpeed
}
