package it.unibo.collektive.alchemist.device.sensors

import it.unibo.collektive.model.Position

/**
 * A sensor that provides location-related information within the environment.
 */
interface LocationSensor {
    /**
     * Returns the coordinates of the node's position inside the environment.
     */
    fun coordinates(): Position
}
