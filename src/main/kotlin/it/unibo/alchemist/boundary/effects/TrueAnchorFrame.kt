package it.unibo.alchemist.boundary.effects

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.geometry.Position as Coordinates
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import kotlin.math.hypot

/**
 * The frame fixed by the anchors (see `AnchorFrame`), rebuilt from their true positions, which the nodes never see:
 * it tells where the coordinates of the nodes lie in the environment.
 */
internal class TrueAnchorFrame private constructor(
    private val origin: DoubleArray,
    private val xAxis: Vector2D,
    private val yAxis: Vector2D,
    anchor2: DoubleArray,
    anchor3: DoubleArray,
) {
    /** The centroid of the anchors, in this frame. */
    val centroid: Coordinates = listOf(anchor2, anchor3).map(::toFrame).let { (a2, a3) ->
        Coordinates((a2.x + a3.x) / 3, (a2.y + a3.y) / 3)
    }

    /** The coordinates in this frame of the environment [position]. */
    fun toFrame(position: DoubleArray): Coordinates {
        val dx = position[0] - origin[0]
        val dy = position[1] - origin[1]
        return Coordinates(dx * xAxis.x + dy * xAxis.y, dx * yAxis.x + dy * yAxis.y)
    }

    /** The [vector] of this frame, in the environment. */
    fun toEnvironment(vector: Vector2D) =
        SpeedControl2D(vector.x * xAxis.x + vector.y * yAxis.x, vector.x * xAxis.y + vector.y * yAxis.y)

    companion object {
        private val LEADER = SimpleMolecule("leader")

        /**
         * The frame of the anchors in [environment], null until they are elected. Anchor 1 is the leader with the
         * highest id among those with two leaders as neighbors (anchors 2 and 3): a node cut off from the swarm leads
         * alone.
         */
        fun <T, P : Position<P>> of(environment: Environment<T, P>): TrueAnchorFrame? {
            val leaders = environment.nodes.filter { it.getConcentration(LEADER) == true }.toSet()
            val (anchor1, partners) = leaders.sortedByDescending { it.id }.firstNotNullOfOrNull { candidate ->
                environment.getNeighborhood(candidate).neighbors.filter { it in leaders }
                    .takeIf { it.size == 2 }
                    ?.let { candidate to it }
            } ?: return null
            val (anchor2, anchor3) = partners // Anchor 2 is the farthest from anchor 1
                .sortedByDescending { environment.getDistanceBetweenNodes(anchor1, it) }
                .map { environment.getPosition(it).coordinates }
            val origin = environment.getPosition(anchor1).coordinates
            val length = hypot(anchor2[0] - origin[0], anchor2[1] - origin[1])
            val xAxis = SpeedControl2D((anchor2[0] - origin[0]) / length, (anchor2[1] - origin[1]) / length)
            val cross = xAxis.x * (anchor3[1] - origin[1]) - xAxis.y * (anchor3[0] - origin[0])
            val side = if (cross > 0) 1.0 else -1.0 // Anchor 3 is on the positive y side
            return TrueAnchorFrame(origin, xAxis, SpeedControl2D(-xAxis.y * side, xAxis.x * side), anchor2, anchor3)
        }
    }
}
