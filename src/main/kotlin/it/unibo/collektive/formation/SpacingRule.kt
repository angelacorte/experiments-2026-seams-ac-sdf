package it.unibo.collektive.formation

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.sdf.SDF

/** How the lattice spacing of a device evolves. */
sealed interface SpacingRule {
    /** The spacing of the device for the [shape], in the [neighborhood]. */
    context(aggregate: Aggregate<Int>)
    fun current(shape: SDF, neighborhood: LatticeNeighborhood): Double

    /** Always the same [spacing]: to fill the shape, pick it from its area and the number of devices. */
    data class Fixed(val spacing: Double) : SpacingRule {
        context(aggregate: Aggregate<Int>)
        override fun current(shape: SDF, neighborhood: LatticeNeighborhood) = spacing
    }

    /**
     * Adapts to the room available, driven by the pressure `spacing - ringRadius` of the [LatticeNeighborhood]: the
     * spacing grows while the ring keeps up, and shrinks when the ring is squeezed (e.g., the shape is too small for
     * all the devices); it holds without neighbors.
     *
     * @property initial the spacing at the start.
     * @property rate the fraction of the pressure error recovered at each round.
     * @property pressureMargin the target pressure, as a fraction of the ring radius.
     * @property max the largest spacing, below the communication range so that no neighbor is pushed out of it.
     */
    data class Adaptive(val initial: Double, val rate: Double, val pressureMargin: Double, val max: Double) :
        SpacingRule {
        context(aggregate: Aggregate<Int>)
        override fun current(shape: SDF, neighborhood: LatticeNeighborhood) = aggregate.evolve(initial) { spacing ->
            val pressure = spacing - neighborhood.ringRadius
            val adapted = when {
                neighborhood.isEmpty -> spacing
                else -> spacing + rate * (pressureMargin * neighborhood.ringRadius - pressure)
            }
            adapted.coerceAtMost(max)
        }
    }

    /**
     * The spacing that fills the shape with all the devices, computed by an elected leader and spread to all (see
     * [electedSpacing]): it is the same for every device and does not depend on the [LatticeNeighborhood].
     *
     * @property initial the spacing until the elected one reaches the device.
     * @property areaFrom the lower corner (on both axes) of the square where the area of the shape is measured.
     * @property areaTo the upper corner (on both axes) of that square.
     */
    data class Elected(val initial: Double, val areaFrom: Double, val areaTo: Double) : SpacingRule {
        context(aggregate: Aggregate<Int>)
        override fun current(shape: SDF, neighborhood: LatticeNeighborhood) = aggregate.electedSpacing(shape, this)
    }

    /**
     * As [Adaptive], with the pressure of the whole neighborhood instead of the ring: the squeeze of all the
     * neighbors [near][LatticeNeighborhood.near] the device, shared as in a full ring (see
     * [LatticeNeighborhood.pressure]), zero when none is, so that the spacing grows until the neighbors come within
     * reach, and keeps squeezing the devices with fewer neighbors than a hexagon.
     *
     * @property pressureMargin the target pressure, as a fraction of the spacing: below `1 / RING_SIZE`, or a lone
     * pair of devices is squeezed together.
     */
    data class Neighborhood(val initial: Double, val rate: Double, val pressureMargin: Double, val max: Double) :
        SpacingRule {
        context(aggregate: Aggregate<Int>)
        override fun current(shape: SDF, neighborhood: LatticeNeighborhood) = aggregate.evolve(initial) { spacing ->
            val adapted = when {
                neighborhood.isEmpty -> spacing
                else -> spacing + rate * (pressureMargin * spacing - neighborhood.pressure(spacing))
            }
            adapted.coerceAtMost(max)
        }
    }
}
