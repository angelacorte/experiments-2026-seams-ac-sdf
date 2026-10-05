package it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.boundary.effects.TrueAnchorFrame
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.collektive.catalog.TargetShape
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private const val SAMPLE_STEP = 0.5 // Side of the cells that sample the shape
private const val WINDOW = 100.0 // Half the side of the square, around the center of the shape, where it is sampled
private const val COVER_QUANTILE = 0.95 // The quantile of the gaps in cover95

/**
 * How evenly the devices cover their shape, measured in the environment from their true positions (which they never
 * see). The share of a device is the part of the shape nearer to it than to any other device, over the fair share
 * (the area of the shape divided by the number of devices).
 * - `inside`: the fraction of devices inside the shape;
 * - `jain`, `shareCV`: Jain's fairness index (1 when all are equal) and coefficient of variation of the shares;
 * - `cover95`, `coverMax`: the 95th percentile and the largest distance of a point of the shape from the nearest
 *   device, in `fill` (the spacing of the hexagonal lattice that would fill the shape with all the devices);
 * - `nnCV`, `minDist`: the variation and the smallest distance (in `fill`) to the nearest neighbor, inside the shape;
 * - `psi6`: how close the 6 nearest neighbors of a device are to a regular hexagon around it (1 when they are), for
 *   the devices more than `fill` deep in the shape;
 * - `borderRatio`: the devices in the band of one lattice row along the border, over its share of the area (1 is fair);
 * - `motion`: the mean displacement per time unit since the previous export, in `fill`.
 *
 * The shape lies where the devices place it, by [placement], with the origin and the axes of its frame taken from the
 * environment:
 * - `global`: where `shapes.yml` puts it (global positions, `repulsionOnly.yml`);
 * - `leader`: its center on the true position of the leader, with the axes of the environment, in which the relative
 *   sensor measures (`relativeToLeader.yml`);
 * - `anchors`: its center on the centroid of the anchors, in the frame they fix, possibly turned and mirrored
 *   (`rangeOnly.yml`, see [TrueAnchorFrame]).
 * Until the leader or the anchors are elected, every column is NaN.
 */
class FormationMetrics(private val placement: String) : AbstractDoubleExtractor() {
    init {
        requireKnownPlacement(placement)
    }

    private val samples = mutableMapOf<TargetShape, ShapeSamples>() // Of each shape where shapes.yml puts it, computed once
    private var previous: Pair<Double, Map<Int, Position>>? = null

    override val columnNames: List<String> = COLUMNS

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long,
    ): Map<String, Double> {
        val (shape, where) = shapeIn(environment, placement) ?: return NOT_AVAILABLE
        val positions = environment.nodes.associate { node ->
            node.id to environment.getPosition(node).coordinates.let { Position(it[0], it[1]) }
        }
        val canonical = samples.getOrPut(shape) { ShapeSamples(shape.sdf, shape.center) }
        val measures = measureFormation(where.sdf, canonical.moved(where::toEnvironment), positions.values.toList())
        val now = time.toDouble()
        val motion = previous?.takeIf { (then, _) -> now > then }?.let { (then, before) ->
            positions.mapNotNull { (id, p) -> before[id]?.let { hypot(p.x - it.x, p.y - it.y) } }
                .average() / (now - then) / measures.values.getValue("fill")
        } ?: Double.NaN
        previous = now to positions
        return measures.values + ("motion" to motion)
    }

    /** Constants of [FormationMetrics]. */
    companion object {
        /** The columns, as described in [FormationMetrics]. */
        val COLUMNS = listOf(
            "inside", "jain", "shareCV", "cover95", "coverMax", "nnCV", "minDist", "psi6", "borderRatio", "fill",
            "motion",
        )
        private val NOT_AVAILABLE = COLUMNS.associateWith { Double.NaN }
    }
}

/** The [points] of a shape on a grid of side [SAMPLE_STEP], with their [depths] below its border. */
class ShapeSamples private constructor(val points: List<Position>, val depths: List<Double>) {
    /** The samples of [shape] within [WINDOW] of its [center]. */
    constructor(shape: SDF, center: Position) : this(sample(shape, center))

    private constructor(samples: List<Pair<Position, Double>>) : this(
        samples.map {
            it.first
        },
        samples.map { it.second },
    )

    /** The number of samples. */
    val size: Int get() = points.size

    /** The area of the shape. */
    val area: Double get() = size * SAMPLE_STEP * SAMPLE_STEP

    /** These samples moved to [where] they lie (rigidly: the depths stay). */
    fun moved(where: (Position) -> Position) = ShapeSamples(points.map(where), depths)

    private companion object {
        fun sample(shape: SDF, center: Position): List<Pair<Position, Double>> {
            val side = (2 * WINDOW / SAMPLE_STEP).toInt()
            return (0 until side * side)
                .map {
                    center.shifted((it / side + 0.5) * SAMPLE_STEP - WINDOW, (it % side + 0.5) * SAMPLE_STEP - WINDOW)
                }
                .mapNotNull { point -> (-shape(point)).takeIf { it >= 0.0 }?.let { point to it } }
                .also { inside ->
                    val edge = WINDOW - 1
                    require(inside.none { (p, _) -> abs(p.x - center.x) > edge || abs(p.y - center.y) > edge }) {
                        "The shape reaches farther than $WINDOW from its center: enlarge WINDOW"
                    }
                }
        }

        fun Position.shifted(dx: Double, dy: Double) = Position(x + dx, y + dy)
    }
}

/**
 * The metrics of [FormationMetrics] (all but the motion), and the share of each device.
 *
 * @property values the metrics, by column name.
 * @property shares the share of the shape of each device, in the order of the points (1 is fair).
 */
class FormationMeasures(val values: Map<String, Double>, val shares: List<Double>)

/** The [FormationMeasures] of the devices at [points], for the [shape] sampled by [samples]. */
fun measureFormation(shape: SDF, samples: ShapeSamples, points: List<Position>): FormationMeasures {
    if (points.isEmpty() || samples.size == 0) return FormationMeasures(emptyMap(), points.map { 0.0 })
    val devices = points.size
    val fill = sqrt(2 * samples.area / (sqrt(3.0) * devices)) // Spacing of the hexagonal lattice that fills the shape
    // The nearest device to each sample: the Voronoi cells, clipped to the shape
    val owners = samples.points.map { sample -> points.indices.minBy { points[it].squaredDistanceTo(sample) } }
    val gaps = samples.points.zip(owners) { sample, owner -> sqrt(points[owner].squaredDistanceTo(sample)) }.sorted()
    val owned = owners.groupingBy { it }.eachCount()
    val shares = points.indices.map { owned.getOrDefault(it, 0) * devices.toDouble() / samples.size }
    val mean = shares.average()
    val depths = points.map { -shape(it) }
    val inside = points.indices.filter { depths[it] >= 0.0 }
    val neighbors = inside.associateWith { device ->
        points.indices.filter { it != device }.sortedBy { points[it].squaredDistanceTo(points[device]) }
    }
    val nearest = neighbors.mapNotNull { (device, around) ->
        around.firstOrNull()?.let { sqrt(points[it].squaredDistanceTo(points[device])) }
    }
    val nearestMean = nearest.average()
    val psi6 = inside.filter { depths[it] > fill }.map { device ->
        val angles = neighbors.getValue(device).take(6).map { 6 * points[device].angleTo(points[it]) }
        hypot(angles.sumOf(::cos), angles.sumOf(::sin)) / 6
    }
    val band = sqrt(3.0) / 2 * fill // One row of the lattice along the border
    val devicesInBand = inside.count { depths[it] < band }.toDouble() / inside.size
    val samplesInBand = samples.depths.count { it < band }.toDouble() / samples.size
    return FormationMeasures(
        mapOf(
            "inside" to inside.size.toDouble() / devices,
            "jain" to shares.sum().let { it * it } / (devices * shares.sumOf { it * it }),
            "shareCV" to sqrt(shares.sumOf { (it - mean) * (it - mean) } / devices) / mean,
            "cover95" to gaps[(COVER_QUANTILE * (gaps.size - 1)).toInt()] / fill,
            "coverMax" to gaps.last() / fill,
            "nnCV" to sqrt(nearest.sumOf { (it - nearestMean) * (it - nearestMean) } / nearest.size) / nearestMean,
            "minDist" to (nearest.minOrNull() ?: Double.NaN) / fill,
            "psi6" to psi6.average(),
            "borderRatio" to devicesInBand / samplesInBand,
            "fill" to fill,
        ),
        shares,
    )
}

private fun Position.squaredDistanceTo(other: Position) = (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)

private fun Position.angleTo(other: Position) = atan2(other.y - y, other.x - x)
