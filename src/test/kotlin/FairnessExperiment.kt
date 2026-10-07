import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.boundary.extractors.FormationMetrics
import it.unibo.alchemist.boundary.extractors.ShapeSamples
import it.unibo.alchemist.boundary.extractors.measureFormation
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.geometry.Position
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

private const val RANGE = 20.0 // Communication range, as in repulsionOnly.yml
private const val MAX_SPACING = 0.8 * RANGE
private val SNAPSHOTS = listOf(50.0, 100.0, 250.0, 500.0, 900.0, 1000.0)

private const val SOFT_DISK = "{ type: SoftDisk, parameters: { stiffness: 0.2 } }"
private const val SPRING = "{ type: Spring, parameters: { stiffness: 0.2, attraction: 0.3 } }"
private const val ADAPTIVE =
    "{ type: Adaptive, parameters: { initial: 2.0, rate: 0.05, pressureMargin: 0.5, max: $MAX_SPACING } }"

private fun neighborhood(initial: Double) =
    "{ type: Neighborhood, parameters: { initial: $initial, rate: 0.05, pressureMargin: 0.1, max: $MAX_SPACING } }"

private fun elected(initial: Double, margin: Double = 0.0) =
    "{ type: Elected, parameters: { initial: $initial, areaFrom: 0.0, areaTo: 100.0, margin: $margin } }"

/** The repulsion and the spacing of each variant, given the spacing of the deployment. */
private val variants: Map<String, (Double) -> Pair<String, String>> = mapOf(
    "SoftDisk+Adaptive" to { _ -> SOFT_DISK to ADAPTIVE },
    "InverseSquare+Adaptive" to { _ -> "{ type: InverseSquare, parameters: { coefficient: 0.03 } }" to ADAPTIVE },
    "Spring+Neighborhood" to { initial -> SPRING to neighborhood(initial) },
    "SoftDisk+Elected" to { initial -> SOFT_DISK to elected(initial) },
    "Spring+Elected" to { initial -> SPRING to elected(initial) },
    "Spring+Elected25" to { initial -> SPRING to elected(initial, margin = 0.25) },
    "Lloyd" to { _ ->
        "{ type: Lloyd, parameters: { gain: 0.5, reach: ${RANGE / 2} } }" to
            "{ type: Fixed, parameters: { spacing: 4.0 } }"
    },
    "LloydGain1+FixedStep" to { _ ->
        // Classic Lloyd: a full jump to the centroid, with the step size held (FIXED_STEP)
        "{ type: Lloyd, parameters: { gain: 1.0, reach: ${RANGE / 2} } }" to
            "{ type: Fixed, parameters: { spacing: 4.0 } }"
    },
    "LloydGain1.5+FixedStep" to { _ ->
        // Over-relaxed: beyond the centroid, to spread a crowd faster
        "{ type: Lloyd, parameters: { gain: 1.5, reach: ${RANGE / 2} } }" to
            "{ type: Fixed, parameters: { spacing: 4.0 } }"
    },
    "LloydGain1.8+FixedStep" to { _ ->
        "{ type: Lloyd, parameters: { gain: 1.8, reach: ${RANGE / 2} } }" to
            "{ type: Fixed, parameters: { spacing: 4.0 } }"
    },
)

private const val ADAPTIVE_STEP = "{ type: StepRule, parameters: { decrease: 0.5, increase: 1.2, minGain: 0.05 } }"

// The gain never changes: Lloyd's step towards the centroid is already stable
private const val FIXED_STEP = "{ type: StepRule, parameters: { decrease: 1.0, increase: 1.0, minGain: 1.0 } }"

private fun yaml(seed: Int, nodes: Int, shape: String, repulsion: String, spacing: String, step: String) = """
incarnation: collektive
seeds: { scenario: $seed, simulation: $seed }
network-model: { type: ConnectWithinDistance, parameters: [$RANGE] }
_pool: &program
  - time-distribution: 1
    type: Event
    actions:
      - type: RunCollektiveProgram
        parameters: [it.unibo.collektive.entrypoint.RepulsionOnlyKt.repulsionOnlyEntrypoint]
      - type: SpeedToTarget
        parameters: []
deployments:
  - type: Rectangle
    parameters: [$nodes, 0, 0, 100, 100]
    programs:
      - *program
    properties:
      - type: it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
        parameters: [$shape]
      - type: it.unibo.collektive.alchemist.device.sensors.impl.LocationSensorProperty
      - type: it.unibo.collektive.formation.LatticeParameters
        parameters:
          repulsion: $repulsion
          maxSpeed: 1.0
          spacing: $spacing
          step: $step
"""

/**
 * How evenly each control spreads the devices over the shape: every variant, on several shapes, numbers of devices
 * and seeds, with global positions (the entrypoint of repulsionOnly.yml). Slow: it runs only with `FAIRNESS=true`,
 * and writes `data/fairness/metrics.csv` (a row per snapshot) and `data/fairness/positions.csv` (the final layouts).
 * `FAIRNESS_FILTER` (a regex on `variant/shape/nodes/seed`) and `FAIRNESS_SEEDS` restrict the runs,
 * `FAIRNESS_RESUME=true` skips those already completed.
 */
@EnabledIfEnvironmentVariable(named = "FAIRNESS", matches = "true")
class FairnessExperiment {
    private val shapes = listOf("star", "hexagon", "ring", "crescent", "horseshoe")
    private val targets = shapes.associateWith { ShapeCatalog.named(it) }
    private val samples = targets.mapValues { (_, target) -> ShapeSamples(target.sdf, target.center) }
    private val output = File("data/fairness").apply { mkdirs() }
    private val metricsFile = File(output, "metrics.csv")
    private val positionsFile = File(output, "positions.csv")

    @Test
    fun run() {
        val filter = Regex(System.getenv("FAIRNESS_FILTER") ?: ".*")
        val seeds = System.getenv("FAIRNESS_SEEDS")?.toInt() ?: 5
        val runs = variants.keys.flatMap { variant ->
            shapes.flatMap { shape ->
                listOf(300, 200, 100, 50).flatMap { nodes -> (0 until seeds).map { listOf(variant, shape, nodes, it) } }
            }
        }.filter { filter.containsMatchIn(it.joinToString("/")) }.sortedByDescending { it[2] as Int }

        // FAIRNESS_RESUME=true keeps the runs completed by a previous execution, and drops the partial ones
        fun key(row: String) = row.split(",").take(4).joinToString(",")
        fun rows(file: File) = if (file.exists()) file.readLines().drop(1) else emptyList()
        val completed = when (System.getenv("FAIRNESS_RESUME")) {
            "true" -> rows(metricsFile).filter { it.split(",")[4].toDouble() == SNAPSHOTS.last() }.map(::key).toSet()
            else -> emptySet()
        }
        val (oldMetrics, oldPositions) = listOf(metricsFile, positionsFile).map { f ->
            rows(f).filter {
                key(it) in
                    completed
            }
        }
        metricsFile.writeText(
            "variant,shape,nodes,seed,t,${FormationMetrics.COLUMNS.joinToString(",")},seconds\n" +
                oldMetrics.joinToString("") { "$it\n" },
        )
        positionsFile.writeText("variant,shape,nodes,seed,x,y,share\n" + oldPositions.joinToString("") { "$it\n" })
        val todo = runs.filter { it.joinToString(",") !in completed }
        val pool = Executors.newFixedThreadPool(System.getenv("FAIRNESS_THREADS")?.toInt() ?: 8)
        val done = java.util.concurrent.atomic.AtomicInteger()
        todo.map { (variant, shape, nodes, seed) ->
            pool.submit {
                simulate(variant as String, shape as String, nodes as Int, seed as Int)
                if (variant == variants.keys.first() && "Hexagonal lattice,$shape,$nodes,$seed" !in completed) {
                    reference(shape, nodes, seed)
                }
                println("${done.incrementAndGet()}/${todo.size} $variant $shape $nodes $seed")
            }
        }.forEach { it.get() }
        pool.shutdown()
    }

    /** The yardstick: a perfect hexagonal lattice of the filling spacing, randomly shifted and turned, in the shape. */
    private fun reference(shape: String, nodes: Int, seed: Int) {
        val (sdf, center) = targets.getValue(shape)
        val spacing = sqrt(2 * samples.getValue(shape).area / (sqrt(3.0) * nodes))
        val random = java.util.Random(seed.toLong())
        val (angle, shiftX, shiftY) = Triple(random.nextDouble() * PI / 3, random.nextDouble(), random.nextDouble())
        val lattice = (-60..60).flatMap { i -> (-60..60).map { j -> i + shiftX to j + shiftY } }.map { (i, j) ->
            val (u, v) = (i + j / 2) * spacing to j * sqrt(3.0) / 2 * spacing
            Position(center.x + u * cos(angle) - v * sin(angle), center.y + u * sin(angle) + v * cos(angle))
        }.filter { sdf(it) <= 0.0 }
        val measures = measureFormation(sdf, samples.getValue(shape), lattice)
        val values = measures.values + ("motion" to Double.NaN)
        record("Hexagonal lattice", shape, nodes, seed, SNAPSHOTS.last(), values, lattice, measures.shares, 0L)
    }

    private fun record(
        variant: String,
        shape: String,
        nodes: Int,
        seed: Int,
        t: Double,
        values: Map<String, Double>,
        points: List<Position>,
        shares: List<Double>?,
        start: Long,
    ) {
        val key = "$variant,$shape,$nodes,$seed"
        val row = FormationMetrics.COLUMNS.joinToString(",") { "%.5f".format(Locale.ROOT, values.getValue(it)) }
        synchronized(metricsFile) {
            metricsFile.appendText("$key,$t,$row,%.1f\n".format(Locale.ROOT, (System.nanoTime() - start) / 1e9))
            shares?.let { share ->
                positionsFile.appendText(
                    points.indices.joinToString("") {
                        "$key,%.3f,%.3f,%.4f\n".format(Locale.ROOT, points[it].x, points[it].y, share[it])
                    },
                )
            }
        }
    }

    private fun simulate(variant: String, shape: String, nodes: Int, seed: Int) {
        val start = System.nanoTime()
        val initial = min(MAX_SPACING, sqrt(100.0 * 100.0 / (sqrt(3.0) / 2 * nodes))) // Spacing of the deployment
        val (repulsion, spacing) = variants.getValue(variant)(initial)
        val file = File.createTempFile("fairness", ".yml").apply {
            deleteOnExit()
            val step = if (variant.endsWith("FixedStep")) FIXED_STEP else ADAPTIVE_STEP
            writeText(yaml(seed, nodes, shape, repulsion, spacing, step))
        }
        val simulation = LoadAlchemist.from(file).getDefault<Any?, Euclidean2DPosition>()
        val environment = simulation.environment
        val runner = thread { simulation.run() }
        val metrics = FormationMetrics("global") // Global positions: the shape lies where shapes.yml puts it
        for (t in SNAPSHOTS) {
            val reached = simulation.goToTime(DoubleTime(t)) // Only pauses once there: it needs a play
            simulation.play()
            reached.get()
            val positions = environment.nodes.associate { it.id to environment.getPosition(it) }
            check(positions.isNotEmpty() && positions.values.all { it.x.isFinite() && it.y.isFinite() }) {
                "$variant $shape $nodes $seed, t = $t: ${positions.size} devices, " +
                    "${positions.values.count { !it.x.isFinite() || !it.y.isFinite() }} not finite, ${simulation.error}"
            }
            val values = metrics.extractData(environment, null, DoubleTime(t), 0L)
            val points = positions.values.map { Position(it.x, it.y) }
            // The share of each device, for the final layouts
            val shares = if (t == SNAPSHOTS.last()) {
                measureFormation(targets.getValue(shape).sdf, samples.getValue(shape), points).shares
            } else {
                null
            }
            record(variant, shape, nodes, seed, t, values, points, shares, start)
        }
        simulation.terminate().get()
        runner.join()
        check(simulation.error.isEmpty) { "$variant $shape $nodes $seed: ${simulation.error}" }
    }
}
