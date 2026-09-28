package it.unibo.collektive.alchemist.device.sensors

import it.unibo.common.Vector2D

/**
 * A sensor perceiving the motion of the node itself (e.g., wheel encoders), in its own body frame:
 * it tells nothing about where the node is, nor about the other nodes.
 */
interface OdometrySensor {
    /**
     * Returns the displacement accumulated by this node since it started (dead reckoning).
     */
    fun travelled(): Vector2D
}
