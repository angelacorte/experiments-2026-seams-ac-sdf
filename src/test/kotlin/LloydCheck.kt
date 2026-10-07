import it.unibo.collektive.coverage.voronoiCentroid
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LloydCheck {
    private val plane = SDF { -1.0 } // Inside everywhere
    private val hexagon = (0 until 6).map { SpeedControl2D(4.0 * cos(it * PI / 3), 4.0 * sin(it * PI / 3)) }

    @Test
    fun check() {
        // In a hexagonal lattice the cell is centered on the device: it stays
        assertEquals(0.0, voronoiCentroid(plane, Position.origin, hexagon, 10.0).norm, 0.1)
        // Neighbors only on +x: towards the empty room
        assertTrue(voronoiCentroid(plane, Position.origin, hexagon.filter { it.x > 0 }, 10.0).x < -1.0)
        // On the border of a half plane (inside below y = 1): pulled in, away from it
        val halfPlane = SDF { it.y - 1.0 }
        assertTrue(voronoiCentroid(halfPlane, Position.origin, emptyList(), 10.0).y < -1.0)
        // Far outside the shape: the whole cell, centered on a lone device, and away from a neighbor
        assertEquals(0.0, voronoiCentroid(halfPlane, Position(0.0, 50.0), emptyList(), 10.0).norm, 1e-9)
        assertTrue(voronoiCentroid(halfPlane, Position(0.0, 50.0), listOf(SpeedControl2D(2.0, 0.0)), 10.0).x < -1.0)
    }
}
