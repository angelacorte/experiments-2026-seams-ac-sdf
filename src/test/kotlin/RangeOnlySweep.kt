import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * One parameter of `rangeOnly.yml` changed at a time, from the values in the file: which ones make the range-only
 * formation fairer. The parameters are molecules, so they are set on the devices after loading; the exports go to
 * `data/sweep/`, one file per configuration, shape and seed. Slow: it runs only with `SWEEP=true`.
 * `SWEEP_FILTER` (a regex on `configuration/shape/seed`), `SWEEP_SEEDS`, `SWEEP_END`, `SWEEP_NODES` and
 * `SWEEP_THREADS` set the runs.
 */
@EnabledIfEnvironmentVariable(named = "SWEEP", matches = "true")
class RangeOnlySweep {
    // Name, molecules to set, communication range
    private val configurations = listOf(
        Triple("baseline", emptyMap(), 20),
        Triple("range30", emptyMap(), 30),
        Triple("energy2", mapOf("minMotionEnergy" to 2.0), 20),
        Triple("energy10", mapOf("minMotionEnergy" to 10.0), 20),
        Triple("confidence0.03", mapOf("minConfidence" to 0.03), 20),
        Triple("confidence0.3", mapOf("minConfidence" to 0.3), 20),
        Triple("forget0.9", mapOf("forgettingFactor" to 0.9), 20),
        Triple("forget0.99", mapOf("forgettingFactor" to 0.99), 20),
        Triple("anchors5", mapOf("minAnchorsHeight" to 5.0), 20),
        Triple("anchors20", mapOf("minAnchorsHeight" to 20.0), 20),
    )

    @Test
    fun run() {
        val filter = Regex(System.getenv("SWEEP_FILTER") ?: ".*")
        val seeds = System.getenv("SWEEP_SEEDS")?.toInt() ?: 2
        val end = System.getenv("SWEEP_END")?.toDouble() ?: 600.0
        val nodes = System.getenv("SWEEP_NODES")?.toInt() ?: 50
        val runs = (0 until seeds).flatMap { seed ->
            configurations.flatMap { configuration ->
                listOf("star", "ring", "crescent", "horseshoe").map { shape -> Triple(configuration, shape, seed) }
            }
        }.filter { (configuration, shape, seed) -> filter.containsMatchIn("${configuration.first}/$shape/$seed") }
        val pool = Executors.newFixedThreadPool(System.getenv("SWEEP_THREADS")?.toInt() ?: 3)
        val done = AtomicInteger()
        runs.map { (configuration, shape, seed) ->
            pool.submit {
                val (name, molecules, range) = configuration
                val start = System.nanoTime()
                val simulation = LoadAlchemist.from(
                    ClassLoader.getSystemResource("rangeOnly.yml"),
                    listOf(
                        "seeds: { scenario: $seed, simulation: $seed }",
                        "terminate: { type: AfterTime, parameters: [$end] }",
                        "export: [{ type: CSVExporter, parameters: { fileNameRoot: \"$name\", interval: 10.0, " +
                            "exportPath: \"data/sweep\" }, data: [time, { type: " +
                            "it.unibo.alchemist.boundary.extractors.FormationMetrics, parameters: [anchors] }] }]",
                    ),
                ).getWith<Any?, Euclidean2DPosition>(
                    mapOf("nodes" to nodes, "shape" to shape, "communicationRange" to range, "seed" to seed.toDouble()),
                )
                simulation.environment.nodes.forEach { node ->
                    molecules.forEach { (molecule, value) -> node.setConcentration(SimpleMolecule(molecule), value) }
                }
                simulation.play()
                simulation.run()
                check(simulation.error.isEmpty) { "$name $shape $seed: ${simulation.error}" }
                println(
                    "${done.incrementAndGet()}/${runs.size} $name/$shape/$seed in %.0f s"
                        .format(Locale.ROOT, (System.nanoTime() - start) / 1e9),
                )
            }
        }.forEach { it.get() }
        pool.shutdown()
    }
}
