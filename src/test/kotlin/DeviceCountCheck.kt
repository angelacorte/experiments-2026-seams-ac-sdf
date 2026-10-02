import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.entrypoint.star
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.area
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/**
 * How the spacing `d` follows the number of devices: 100 random devices are removed at t = 2000 and at t = 4000, on
 * the star of `starEntry.yml` at its scale, against `sqrt(2 area / (√3 devices))`: writes `build/deviceCount.csv`.
 */
class DeviceCountCheck {
    @Test
    fun removals() {
        val simulation = LoadAlchemist.from(ClassLoader.getSystemResource("starEntry.yml"))
            .getDefault<Any, Euclidean2DPosition>()
        val environment = simulation.environment
        val area = star.area(-200.0, 300.0, 0.25)
        val random = Random(42)
        val runner = thread { simulation.run() }
        val rows = mutableListOf("t,devices,expected,dMedian,dLow,dHigh,lattice,inside")
        var t = 0.0
        for (until in listOf(2000.0, 4000.0, 6000.0)) {
            if (t > 0.0) {
                val removed = environment.nodes.shuffled(random).take(100)
                simulation.schedule { removed.forEach(environment::removeNode) }
            }
            while (t < until) {
                t += 25.0
                val reached = simulation.goToTime(DoubleTime(t)) // Only pauses once there: it needs a play
                simulation.play()
                reached.get()
                val points = environment.nodes.map { environment.getPosition(it).let { p -> Position(p.x, p.y) } }
                val ds = environment.nodes
                    .mapNotNull { (it.getConcentration(SimpleMolecule("desiredDistance")) as? Number)?.toDouble() }
                    .sorted()
                val inside = points.filter { star(it) <= 0.0 }
                val lattice = inside.map { p ->
                    points.filter { it != p }.map { hypot(it.x - p.x, it.y - p.y) }.sorted().take(3).average()
                }.average()
                val expected = sqrt(2 * area / (sqrt(3.0) * points.size))
                fun quantile(q: Double) = ds[((ds.size - 1) * q).toInt()]
                rows += "$t,${points.size},%.4f,%.4f,%.4f,%.4f,%.4f,${inside.size}"
                    .format(expected, quantile(0.5), quantile(0.1), quantile(0.9), lattice)
            }
            println("devices ${rows.last()}")
        }
        simulation.terminate().get()
        runner.join()
        File("build/deviceCount.csv").writeText(rows.joinToString("\n"))
    }
}
