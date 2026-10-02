import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.shape.Star
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test

class StarEntryCheck {
    private val star = Star(Position(50.0, 50.0), radius = 45.0, pointCount = 5)
    private val grid = (0..100).flatMap { x -> (0..100).map { y -> Position(x.toDouble(), y.toDouble()) } }
        .filter { star(it) <= 0.0 }
    private val tips = grid.filter { hypot(it.x - 50.0, it.y - 50.0) > 0.618 * 45.0 } // Beyond the inner vertices

    @Test
    fun compare() {
        for (entering in listOf(false, true)) {
            val simulation = LoadAlchemist.from(ClassLoader.getSystemResource("starEntry.yml"))
                .getDefault<Any, Euclidean2DPosition>()
            val environment = simulation.environment
            environment.nodes.forEach { it.setConcentration(SimpleMolecule("entering"), entering) }
            val runner = thread { simulation.run() }
            for (t in listOf(100.0, 250.0, 500.0, 1000.0, 2000.0)) {
                val reached = simulation.goToTime(DoubleTime(t)) // Only pauses once there: it needs a play
                simulation.play()
                reached.get()
                val points = environment.nodes.map { environment.getPosition(it).let { p -> Position(p.x, p.y) } }
                val inside = points.count { star(it) <= 0.0 }
                val minDistance = points.indices.minOf { i ->
                    (i + 1 until points.size).minOfOrNull { j ->
                        hypot(points[i].x - points[j].x, points[i].y - points[j].y)
                    } ?: Double.MAX_VALUE
                }
                val fill = sqrt(2 * grid.size / (sqrt(3.0) * points.size))
                val covered = grid.count { g -> points.any { hypot(it.x - g.x, it.y - g.y) <= fill } }
                // Devices in the tips over their share at uniform density (1 is fair, below 1 the tips are emptier)
                val tipShare = points.count { star(it) <= 0.0 && hypot(it.x - 50.0, it.y - 50.0) > 0.618 * 45.0 } /
                    (inside * tips.size.toDouble() / grid.size)
                println(
                    "entering=$entering t=$t inside=$inside/${points.size} minDistance=%.3f coverage=%.3f tipShare=%.3f"
                        .format(minDistance, covered.toDouble() / grid.size, tipShare),
                )
            }
            simulation.terminate().get()
            runner.join()
        }
    }
}
