import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.boundary.extractors.FormationMetrics
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.geometry.SpeedControl2D
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * How the devices of distanceBased.yml explore, detach and flicker. At each of FLICKER_SNAPSHOTS: the share of devices
 * exploring (a command that is not the control), cut off from the anchors (out of their connected component), inside
 * the shape, and Jain's index. From the last snapshot, for FLICKER_WINDOW rounds, the true position of every device at
 * each round: the mean step, the share of steps that reverse the previous one, and the path over the net displacement.
 * FLICKER_MOLECULES (`name=value,...`) overrides the molecules of the file.
 */
@EnabledIfEnvironmentVariable(named = "FLICKER", matches = "true")
class FlickerProbe {
    @Test
    fun run() {
        val env = System.getenv()
        val snapshots = (env["FLICKER_SNAPSHOTS"] ?: "10,25,50,100,200,400").split(",").map { it.toDouble() }
        val window = env["FLICKER_WINDOW"]?.toInt() ?: 50
        val variables = mapOf(
            "nodes" to (env["FLICKER_NODES"]?.toInt() ?: 100),
            "shape" to (env["FLICKER_SHAPE"] ?: "hexagon"),
            "communicationRange" to (env["FLICKER_RANGE"]?.toInt() ?: 30),
        )
        val seed = env["FLICKER_SEED"] ?: "42"
        val simulation = LoadAlchemist.from(
            ClassLoader.getSystemResource("distanceBased.yml"),
            listOf("export: []", "seeds: { scenario: $seed, simulation: $seed }"),
        ).getWith<Any?, Euclidean2DPosition>(variables)
        val environment = simulation.environment
        val molecules = env["FLICKER_MOLECULES"].orEmpty().split(",").filter { it.isNotBlank() }
            .map { it.split("=") }.associate { (name, value) -> name to value.toDouble() }
        environment.nodes.forEach { node ->
            molecules.forEach { (name, value) -> node.setConcentration(SimpleMolecule(name), value) }
        }
        val runner = thread { simulation.run() }
        fun goTo(t: Double) {
            val reached = simulation.goToTime(DoubleTime(t))
            simulation.play()
            reached.get()
        }
        val metrics = FormationMetrics("anchors")
        val series = snapshots.map { t ->
            goTo(t)
            val values = metrics.extractData(environment, null, DoubleTime(t), 0L)
            "t=%.0f exploring=%.2f cutOff=%.2f inside=%.2f jain=%.3f".format(
                Locale.ROOT,
                t,
                environment.nodes.count(::exploring).toDouble() / environment.nodes.size,
                cutOff(environment),
                values["inside"],
                values["jain"],
            )
        }
        val tracks = mutableMapOf<Int, MutableList<Pair<Double, Double>>>()
        for (t in 0..window) {
            goTo(snapshots.last() + t)
            environment.nodes.forEach { node ->
                val p = environment.getPosition(node)
                tracks.getOrPut(node.id) { mutableListOf() } += p.x to p.y
            }
        }
        simulation.terminate().get()
        runner.join()
        val stats = tracks.values.map { track ->
            val steps = track.zipWithNext { (ax, ay), (bx, by) -> bx - ax to by - ay }
            val lengths = steps.map { (x, y) -> hypot(x, y) }
            val reversals = steps.zipWithNext { (ax, ay), (bx, by) -> ax * bx + ay * by < 0.0 }.count { it }
            val net = hypot(track.last().first - track.first().first, track.last().second - track.first().second)
            Triple(lengths.average(), reversals.toDouble() / (steps.size - 1), lengths.sum() / maxOf(net, 1e-9))
        }
        println("FLICKER $molecules $variables seed=$seed")
        series.forEach { println("FLICKER   $it") }
        println(
            "FLICKER   then $window rounds: step=%.3f reversals=%.2f path/net=%.1f".format(
                Locale.ROOT,
                stats.map { it.first }.average(),
                stats.map { it.second }.average(),
                stats.map { it.third }.sorted()[stats.size / 2],
            ),
        )
    }

    // Steering commands the control (turned, same length); exploring does not. The anchors stand still.
    private fun exploring(node: Node<Any?>): Boolean {
        val velocity = node.getConcentration(SimpleMolecule("Velocity")) as? SpeedControl2D
        val control = node.getConcentration(SimpleMolecule("control")) as? SpeedControl2D
        return velocity != null && control != null && node.getConcentration(SimpleMolecule("leader")) != true &&
            abs(velocity.norm - control.norm) > 1e-6
    }

    // The share of devices out of the connected component of the anchors.
    private fun cutOff(environment: Environment<Any?, Euclidean2DPosition>): Double {
        val anchors = environment.nodes.filter { it.getConcentration(SimpleMolecule("leader")) == true }
        val reached = anchors.toMutableSet()
        val frontier = ArrayDeque(anchors)
        while (frontier.isNotEmpty()) {
            environment.getNeighborhood(frontier.removeFirst()).neighbors.filter(reached::add).forEach(frontier::add)
        }
        return 1.0 - reached.size.toDouble() / environment.nodes.size
    }
}
