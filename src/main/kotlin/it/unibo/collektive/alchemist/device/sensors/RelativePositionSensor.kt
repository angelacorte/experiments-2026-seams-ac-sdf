package it.unibo.collektive.alchemist.device.sensors

import it.unibo.common.Vector2D

/**
 * A sensor perceiving the relative displacement (dx, dy) w.r.t. neighboring nodes, without global positioning.
 */
interface RelativePositionSensor {
    /**
     * Returns the vector going from [neighbor] to this node (i.e., `self - neighbor`).
     */
    fun relativeTo(neighbor: Int): Vector2D
}
