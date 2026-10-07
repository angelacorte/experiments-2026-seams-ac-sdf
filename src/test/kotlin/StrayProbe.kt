import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.boundary.effects.TrueAnchorFrame
import it.unibo.alchemist.boundary.extractors.shapeIn
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Why devices of dynamicPopulationDistanceBased.yml leave the swarm: every round up to STRAY_UNTIL, for the devices
 * whose nearest neighbor ends farther than the range, the true step, the control turned into the environment by the
 * true anchor frame (same direction as the step when the alignment is right), the estimated and the true SDF.
 */
@EnabledIfEnvironmentVariable(named = "STRAY", matches = "true")
class StrayProbe {
    @Test
    fun run() {
        val env = System.getenv()
        val until = env["STRAY_UNTIL"]?.toInt() ?: 120
        val seed = env["STRAY_SEED"] ?: "0"
        val simulation = LoadAlchemist.from(
            ClassLoader.getSystemResource("dynamicPopulationDistanceBased.yml"),
            listOf("export: []", "seeds: { scenario: $seed, simulation: $seed }"),
        ).getWith<Any?, Euclidean2DPosition>(mapOf("shape" to (env["STRAY_SHAPE"] ?: "circle")))
        val environment = simulation.environment
        val runner = thread { simulation.run() }
        val rows = mutableMapOf<Int, MutableList<String>>()
        val nearest = mutableMapOf<Int, Double>()
        val last = mutableMapOf<Int, DoubleArray>()
        for (t in 1..until) {
            simulation.goToTime(DoubleTime(t.toDouble())).also { simulation.play() }.get()
            val frame = TrueAnchorFrame.of(environment)
            val sdf = shapeIn(environment, "anchors")?.second?.sdf
            environment.nodes.forEach { node ->
                val p = environment.getPosition(node).coordinates
                val before = last.put(node.id, p) ?: p
                val step = SpeedControl2D(p[0] - before[0], p[1] - before[1])
                val control = node.getConcentration(SimpleMolecule("control")) as? SpeedControl2D
                val wanted = control?.let { frame?.toEnvironment(it) }
                val cos = wanted?.takeIf { it.norm > 1e-9 && step.norm > 1e-9 }
                    ?.let { (it.x * step.x + it.y * step.y) / (it.norm * step.norm) }
                nearest[node.id] = environment.nodes.filter { it != node }
                    .minOfOrNull { environment.getDistanceBetweenNodes(node, it) } ?: Double.NaN
                val linked = environment.getNeighborhood(node).neighbors
                    .map { environment.getDistanceBetweenNodes(node, it) }
                rows.getOrPut(node.id) { mutableListOf() } +=
                    "t=%d nn=%.1f links=%d linkedNn=%.1f step=%.2f control=%.2f cos=%s est=%s true=%s"
                        .format(
                            Locale.ROOT, t, nearest[node.id], linked.size, linked.minOrNull() ?: Double.NaN, step.norm,
                            control?.norm ?: Double.NaN,
                            cos?.let { "%.2f".format(Locale.ROOT, it) } ?: "-",
                            (node.getConcentration(SimpleMolecule("distanceToSDF")) as? Double)
                                ?.let { "%.1f".format(Locale.ROOT, it) } ?: "-",
                            sdf?.invoke(Position(p[0], p[1]))?.let { "%.1f".format(Locale.ROOT, it) } ?: "-",
                        )
            }
        }
        simulation.terminate().get()
        runner.join()
        nearest.filterValues { it > 30.0 }.keys.forEach { id ->
            println("STRAY device $id")
            rows.getValue(id).forEach { println("STRAY   $it") }
        }
    }
}
