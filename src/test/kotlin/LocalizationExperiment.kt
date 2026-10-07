import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * The formation with global positions, relative to a leader and range-only, as the simulation files set it up,
 * measured by their own exports (FormationMetrics, in `data/`): only the seeds, the end and the variables are
 * overridden.
 * Slow: it runs only with `LOCALIZATION=true`. `LOCALIZATION_FILTER` (a regex on `scenario/shape/nodes/seed`),
 * `LOCALIZATION_SEEDS`, `LOCALIZATION_END` and `LOCALIZATION_THREADS` set the runs. The final layouts go to
 * `data/localization-positions.csv`, with the leaders (or anchors) and the distance from the shape each device
 * believes.
 */
@EnabledIfEnvironmentVariable(named = "LOCALIZATION", matches = "true")
class LocalizationExperiment {
    @Test
    fun run() {
        val filter = Regex(System.getenv("LOCALIZATION_FILTER") ?: ".*")
        val seeds = System.getenv("LOCALIZATION_SEEDS")?.toInt() ?: 2
        val end = System.getenv("LOCALIZATION_END")?.toDouble() ?: 1000.0
        val runs = listOf("repulsionOnly", "relativeToLeader", "rangeOnly").flatMap { scenario ->
            listOf("star", "hexagon", "ring", "crescent", "horseshoe").flatMap { shape ->
                listOf(50, 100, 200).flatMap { nodes ->
                    (0 until seeds).map { seed -> Run(scenario, shape, nodes, seed) }
                }
            }
        }.filter { filter.containsMatchIn(it.toString()) }.sortedBy { it.nodes } // Cheap first: a stop leaves a grid
        val layouts = File("data/localization-positions.csv").apply {
            writeText("scenario,shape,nodes,seed,x,y,leader,believed\n")
        }
        val pool = Executors.newFixedThreadPool(System.getenv("LOCALIZATION_THREADS")?.toInt() ?: 3)
        val done = AtomicInteger()
        runs.map { run ->
            pool.submit {
                val start = System.nanoTime()
                val simulation = LoadAlchemist.from(
                    ClassLoader.getSystemResource("${run.scenario}.yml"),
                    listOf(
                        "seeds: { scenario: ${run.seed}, simulation: ${run.seed} }",
                        "terminate: { type: AfterTime, parameters: [$end] }",
                    ),
                ).getWith<Any?, Euclidean2DPosition>(
                    mapOf(
                        "nodes" to run.nodes,
                        "shape" to run.shape,
                        "communicationRange" to 20,
                        "seed" to run.seed.toDouble(),
                    ),
                )
                simulation.play()
                simulation.run()
                check(simulation.error.isEmpty) { "$run: ${simulation.error}" }
                val environment = simulation.environment
                val rows = environment.nodes.joinToString("") { node ->
                    val p = environment.getPosition(node)
                    "%s,%s,%d,%d,%.3f,%.3f,%b,%s\n".format(
                        Locale.ROOT, run.scenario, run.shape, run.nodes, run.seed, p.x, p.y,
                        node.getConcentration(SimpleMolecule("leader")) == true,
                        node.getConcentration(SimpleMolecule("distanceToSDF")) ?: "NaN",
                    )
                }
                synchronized(layouts) { layouts.appendText(rows) }
                println(
                    "${done.incrementAndGet()}/${runs.size} $run in %.0f s"
                        .format(Locale.ROOT, (System.nanoTime() - start) / 1e9),
                )
            }
        }.forEach { it.get() }
        pool.shutdown()
    }

    private data class Run(val scenario: String, val shape: String, val nodes: Int, val seed: Int) {
        override fun toString() = "$scenario/$shape/$nodes/$seed"
    }
}
