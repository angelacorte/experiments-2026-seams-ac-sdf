import it.unibo.alchemist.boundary.LoadAlchemist
import it.unibo.alchemist.model.Node.Companion.asProperty
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.formation.LatticeParameters
import it.unibo.collektive.formation.RepulsionLaw
import it.unibo.collektive.formation.SpacingRule
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every simulation file builds the [LatticeParameters] of its devices, and runs for a while. */
class YamlLoadCheck {
    @Test
    fun load() {
        for (file in listOf(
            "repulsionOnly.yml",
            "starEntry.yml",
            "rangeOnly.yml",
            "relativeToLeader.yml",
            "dynamicPopulation.yml",
        )) {
            val simulation = LoadAlchemist.from(ClassLoader.getSystemResource(file))
                .getDefault<Any?, Euclidean2DPosition>()
            val parameters: LatticeParameters<Any?> = simulation.environment.nodes.first().asProperty()
            if (file == "starEntry.yml") {
                assertTrue(parameters.repulsion is RepulsionLaw.Spring)
                assertEquals(SpacingRule.Neighborhood(4.8, 0.05, 0.1, 0.8 * 18), parameters.spacing)
                assertEquals(1.2, parameters.step.increase)
            }
            val runner = thread { simulation.run() }
            val reached = simulation.goToTime(DoubleTime(20.0))
            simulation.play()
            reached.get()
            simulation.terminate().get()
            runner.join()
            assertTrue(simulation.error.isEmpty, "$file: ${simulation.error}")
        }
    }
}
