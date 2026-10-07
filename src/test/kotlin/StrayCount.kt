import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.boundary.extractors.FormationMetrics
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * How many devices of dynamicPopulationDistanceBased.yml end up out of range of every other one at STRAY_UNTIL, for
 * each shape of STRAY_SHAPES and seed of STRAY_SEEDS, with the molecules of STRAY_MOLECULES (`name=value,...`) and the
 * network model of STRAY_NETWORK (the yaml of `network-model`, e.g. `{ type: AdaptiveRange, parameters: [...] }`).
 */
@EnabledIfEnvironmentVariable(named = "STRAY_COUNT", matches = "true")
class StrayCount {
    @Test
    fun run() {
        val env = System.getenv()
        val until = env["STRAY_UNTIL"]?.toDouble() ?: 250.0
        val molecules = env["STRAY_MOLECULES"].orEmpty().split(",").filter { it.isNotBlank() }
            .map { it.split("=") }.associate { (name, value) -> name to value.toDouble() }
        val runs = (env["STRAY_SHAPES"] ?: "circle,star").split(",").flatMap { shape ->
            (env["STRAY_SEEDS"] ?: "0,1,2,3,4,5").split(",").map { shape to it }
        }
        val pool = Executors.newFixedThreadPool(3)
        val counts = runs.map { (shape, seed) ->
            pool.submit<Pair<Int, Double>> {
                val simulation = LoadAlchemist.from(
                    ClassLoader.getSystemResource("dynamicPopulationDistanceBased.yml"),
                    listOf("export: []", "seeds: { scenario: $seed, simulation: $seed }") +
                        listOfNotNull(env["STRAY_NETWORK"]?.let { "network-model: $it" }),
                ).getWith<Any?, Euclidean2DPosition>(mapOf("shape" to shape))
                val environment = simulation.environment
                environment.nodes.forEach { node ->
                    molecules.forEach { (name, value) -> node.setConcentration(SimpleMolecule(name), value) }
                }
                val runner = thread { simulation.run() }
                simulation.goToTime(DoubleTime(until)).also { simulation.play() }.get()
                val strays = environment.nodes.count { node ->
                    environment.nodes.none { it != node && environment.getDistanceBetweenNodes(node, it) <= RANGE }
                }
                val inside = FormationMetrics("anchors").extractData(environment, null, DoubleTime(until), 0L)["inside"]
                simulation.terminate().get()
                runner.join()
                strays to (inside ?: Double.NaN)
            }
        }.map { it.get() }
        pool.shutdown()
        runs.zip(counts).forEach { (run, count) ->
            println(
                "STRAYCOUNT $molecules ${run.first} seed ${run.second}: ${count.first} strays, inside ${count.second}",
            )
        }
        println(
            "STRAYCOUNT $molecules ${env["STRAY_NETWORK"].orEmpty()} total ${counts.sumOf {
                it.first
            }} over ${runs.size} runs",
        )
    }

    private companion object {
        const val RANGE = 30.0
    }
}
