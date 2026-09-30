package it.unibo.collektive.formation

/** How the lattice spacing of a device evolves. */
sealed interface SpacingRule {
    /** The spacing at the start. */
    val initial: Double

    /** The spacing that follows [spacing] in the [neighborhood]. */
    fun next(spacing: Double, neighborhood: LatticeNeighborhood): Double

    /** Always the same [spacing]: to fill the shape, pick it from its area and the number of devices. */
    data class Fixed(val spacing: Double) : SpacingRule {
        override val initial get() = spacing

        override fun next(spacing: Double, neighborhood: LatticeNeighborhood) = this.spacing
    }

    /**
     * Adapts to the room available, driven by the pressure `spacing - ringRadius` of the [LatticeNeighborhood]: the
     * spacing grows while the ring keeps up, and shrinks when the ring is squeezed (e.g., the shape is too small for
     * all the devices); it holds without neighbors.
     *
     * @property rate the fraction of the pressure error recovered at each round.
     * @property pressureMargin the target pressure, as a fraction of the ring radius.
     * @property max the largest spacing, below the communication range so that no neighbor is pushed out of it.
     */
    data class Adaptive(override val initial: Double, val rate: Double, val pressureMargin: Double, val max: Double) :
        SpacingRule {
        override fun next(spacing: Double, neighborhood: LatticeNeighborhood): Double {
            val pressure = spacing - neighborhood.ringRadius
            val adapted = when {
                neighborhood.isEmpty -> spacing
                else -> spacing + rate * (pressureMargin * neighborhood.ringRadius - pressure)
            }
            return adapted.coerceAtMost(max)
        }
    }
}
