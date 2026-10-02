import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.entrypoint.center
import it.unibo.collektive.entrypoint.star
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.area
import it.unibo.collektive.sdf.scale
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * How the spacing `d` adapts to stars of different scales (`shapeScale` in `starEntry.yml`), against the spacing
 * `sqrt(2 area / (√3 devices))` of a hexagonal lattice that fills the star: writes `build/spacingScale.csv`.
 */
class SpacingScaleCheck {
    private val unitArea = star.area(-200.0, 300.0, 0.25) // The star of the entrypoint, at scale 1

    @Test
    fun scales() {
        val rows = mutableListOf("run,t,scale,expected,dMedian,dLow,dHigh,lattice,inside")
        listOf(0.6, 0.8, 1.0, 1.2).forEach { rows += run("static $it", listOf(it to 2000.0)) }
        rows += run("dynamic", listOf(1.0 to 2000.0, 0.7 to 4000.0, 1.2 to 6000.0))
        File("build/spacingScale.csv").writeText(rows.joinToString("\n"))
    }

    /** Runs the [schedule] of (scale, until when), sampling every 25 time units. */
    private fun run(name: String, schedule: List<Pair<Double, Double>>): List<String> {
        val simulation = LoadAlchemist.from(ClassLoader.getSystemResource("starEntry.yml"))
            .getDefault<Any, Euclidean2DPosition>()
        val environment = simulation.environment
        val runner = thread { simulation.run() }
        val rows = mutableListOf<String>()
        var t = 0.0
        for ((scale, until) in schedule) {
            environment.nodes.forEach { it.setConcentration(SimpleMolecule("shapeScale"), scale) }
            val shape = star.scale(scale, center)
            val expected = sqrt(2 * unitArea * scale * scale / (sqrt(3.0) * environment.nodes.size))
            while (t < until) {
                t += 25.0
                val reached = simulation.goToTime(DoubleTime(t)) // Only pauses once there: it needs a play
                simulation.play()
                reached.get()
                val points = environment.nodes.map { environment.getPosition(it).let { p -> Position(p.x, p.y) } }
                val ds = environment.nodes
                    .mapNotNull { (it.getConcentration(SimpleMolecule("desiredDistance")) as? Number)?.toDouble() }
                    .sorted()
                val inside = points.filter { shape(it) <= 0.0 }
                // The lattice actually formed: the mean distance of the 3 nearest devices, inside the star
                val lattice = inside.map { p ->
                    points.filter { it != p }.map { hypot(it.x - p.x, it.y - p.y) }.sorted().take(3).average()
                }.average()
                fun quantile(q: Double) = ds[((ds.size - 1) * q).toInt()]
                rows += "$name,$t,$scale,%.4f,%.4f,%.4f,%.4f,%.4f,${inside.size}"
                    .format(expected, quantile(0.5), quantile(0.1), quantile(0.9), lattice)
            }
            println("$name scale=$scale t=$t expected=%.3f %s".format(expected, rows.last()))
        }
        simulation.terminate().get()
        runner.join()
        return rows
    }
}
