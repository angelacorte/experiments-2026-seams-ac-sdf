package it.unibo.collektive.formation

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.area
import it.unibo.collektive.stdlib.accumulation.countDevices
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.bellmanFordGradientCast
import kotlin.math.sqrt

private const val ELECTION_BOUND = 200 // Hops: beyond the diameter of the network, so that there is one leader

// Samples per side of the grid that measures the area of the shape (not the lattice)
private const val AREA_SAMPLES = 400

/**
 * The spacing of the hexagonal lattice that fills the [shape] with all the devices, `sqrt(2 area / (√3 devices))`
 * (see [SpacingRule.Elected]): a leader is elected, counts the devices along a spanning tree towards itself, computes
 * the spacing from the area of the shape (which every device knows) and spreads it to all along a gradient.
 * Until the spacing reaches a device, it keeps the initial one of the [rule].
 * The gradients are Bellman-Ford ones, carrying a single value: the fast-repair ones of the library carry whole paths,
 * and slow the simulation down about 15 times.
 */
fun Aggregate<Int>.electedSpacing(shape: SDF, rule: SpacingRule.Elected): Double {
    val isLeader = boundedElection(ELECTION_BOUND) == localId
    val hop = neighboring(1.0)
    val hopsToLeader = bellmanFordGradientCast(
        source = isLeader,
        local = 0.0,
        accumulateData = { fromSource, toNeighbor, _ -> fromSource + toNeighbor },
        metric = hop,
    )
    val devices = countDevices(hopsToLeader)
    val spacing = when {
        isLeader -> {
            val area = shape.area(rule.areaFrom, rule.areaTo, (rule.areaTo - rule.areaFrom) / AREA_SAMPLES)
            sqrt(2 * area / (sqrt(3.0) * devices))
        }
        else -> rule.initial
    }
    return bellmanFordGradientCast(source = isLeader, local = spacing, metric = hop)
}
