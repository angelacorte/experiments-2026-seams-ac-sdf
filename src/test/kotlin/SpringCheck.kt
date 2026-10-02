import it.unibo.collektive.formation.LatticeNeighborhood
import it.unibo.collektive.formation.LocalBorder
import it.unibo.collektive.formation.RepulsionLaw
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.zeroSpeed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringCheck {
    private val spring = RepulsionLaw.Spring(stiffness = 1.0, attraction = 0.5)
    private val outside = LocalBorder(1.0, zeroSpeed) // Outside the shape: no mirrors

    private fun hexagon(radius: Double) = (0 until 6).map {
        SpeedControl2D(
            radius * cos(it * PI / 3),
            radius * sin(it * PI / 3),
        )
    }

    private fun pressure(radius: Double, sides: Int = 6) =
        LatticeNeighborhood(hexagon(radius).take(sides), outside).pressure(4.0)

    @Test
    fun check() {
        assertTrue(spring.force(SpeedControl2D(2.0, 0.0), 4.0).x < 0.0) // Too close: pushed away
        assertTrue(spring.force(SpeedControl2D(5.0, 0.0), 4.0).x > 0.0) // Beyond the spacing: pulled in
        assertEquals(0.0, spring.force(SpeedControl2D(7.0, 0.0), 4.0).norm) // Out of reach: ignored
        assertEquals(0.0, pressure(4.0), 1e-9)
        assertEquals(2.0, pressure(2.0), 1e-9)
        assertEquals(0.0, pressure(7.0))
        // Fewer neighbors than a hexagon: less pressure, more squeeze
        assertEquals(2.0 / 3, pressure(2.0, sides = 2), 1e-9)
        // On the border of a dense crowd (all at x > 0, much closer than the spacing): pushed out of it
        val crowd = (1..40).flatMap { i -> (-40..40).map { j -> SpeedControl2D(i * 0.25, j * 0.25) } }
        assertTrue(LatticeNeighborhood(crowd, outside).repulsion(4.0, spring).x < -1.0)
    }
}
