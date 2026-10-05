package it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Time
import it.unibo.collektive.geometry.Position
import kotlin.math.hypot

/**
 * How the neighborhood of each device sees the shape, measured in the environment from the true positions (as
 * [FormationMetrics], with the same [placement]). For each device with at least one neighbor (itself excluded):
 * - the mean and the variance of the SDF of the shape at its neighbors (negative inside the shape);
 * - the mean distance from it to its neighbors.
 *
 * The columns aggregate them over all those devices:
 * - `sdfMean`: the mean of the neighborhood means of the SDF;
 * - `sdfVariance`: the mean of the neighborhood variances of the SDF (how much the SDF varies around a device);
 * - `sdfMeanVariance`: the variance of the neighborhood means of the SDF (how much it varies between devices);
 * - `neighborDistance`, `neighborDistanceVariance`: the mean and the variance of the mean distances to the neighbors.
 *
 * Until the leader or the anchors are elected, or with no device having neighbors, every column is NaN.
 */
class CoverageMetrics(private val placement: String) : AbstractDoubleExtractor() {
    init {
        requireKnownPlacement(placement)
    }

    override val columnNames: List<String> = COLUMNS

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long,
    ): Map<String, Double> {
        val sdf = shapeIn(environment, placement)?.second?.sdf
        val neighborhoods = environment.nodes
            .map { node ->
                val neighbors = environment.getNeighborhood(node).neighbors
                environment.positionOf(node) to neighbors.map { environment.positionOf(it) }
            }
            .filter { (_, neighbors) -> neighbors.isNotEmpty() }
        return when {
            sdf == null || neighborhoods.isEmpty() -> NOT_AVAILABLE
            else -> {
                val sdfs = neighborhoods.map { (_, neighbors) -> neighbors.map { sdf(it) } }
                val sdfMeans = sdfs.map { it.average() }
                val distances = neighborhoods.map { (device, neighbors) ->
                    neighbors.map { hypot(it.x - device.x, it.y - device.y) }.average()
                }
                mapOf(
                    "sdfMean" to sdfMeans.average(),
                    "sdfVariance" to sdfs.map { it.variance() }.average(),
                    "sdfMeanVariance" to sdfMeans.variance(),
                    "neighborDistance" to distances.average(),
                    "neighborDistanceVariance" to distances.variance(),
                )
            }
        }
    }

    /** Constants of [CoverageMetrics]. */
    companion object {
        /** The columns, as described in [CoverageMetrics]. */
        val COLUMNS = listOf(
            "sdfMean",
            "sdfVariance",
            "sdfMeanVariance",
            "neighborDistance",
            "neighborDistanceVariance",
        )
        private val NOT_AVAILABLE = COLUMNS.associateWith { Double.NaN }
    }
}

private fun <T> Environment<T, *>.positionOf(node: Node<T>) =
    getPosition(node).coordinates.let { Position(it[0], it[1]) }

/** The population variance: the mean squared deviation from the mean. */
private fun List<Double>.variance(): Double = average().let { mean -> sumOf { (it - mean) * (it - mean) } / size }
