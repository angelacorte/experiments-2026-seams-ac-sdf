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
    val origin: DoubleArray,
    val xAxis: Vector2D,
    val yAxis: Vector2D,
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
        private val ANCHOR = SimpleMolecule("anchor")

        /**
         * The frame of the anchors in [environment], null until they are elected. The anchors are told apart by their
         * `anchor` molecule (the name of their `AnchorRole`), as they need not be neighbors: anchor 1 is the nearest
         * one to an anchor 2 (a node cut off from the swarm leads alone), anchors 2 and 3 the nearest ones to it.
         */
        fun <T, P : Position<P>> of(environment: Environment<T, P>): TrueAnchorFrame? {
            fun withRole(role: String) = environment.nodes.filter { it.getConcentration(ANCHOR) == role }
            val (origin, anchor2, anchor3) = withRole("ANCHOR_1")
                .minByOrNull { anchor1 ->
                    withRole("ANCHOR_2").minOfOrNull { environment.getDistanceBetweenNodes(anchor1, it) }
                        ?: Double.MAX_VALUE
                }
                ?.let { anchor1 ->
                    listOf(anchor1) + listOf("ANCHOR_2", "ANCHOR_3").mapNotNull { role ->
                        withRole(role).minByOrNull { environment.getDistanceBetweenNodes(anchor1, it) }
                    }
                }
                ?.takeIf { it.size == 3 }
                ?.map { environment.getPosition(it).coordinates }
                ?: return null
            val length = hypot(anchor2[0] - origin[0], anchor2[1] - origin[1])
            val xAxis = SpeedControl2D((anchor2[0] - origin[0]) / length, (anchor2[1] - origin[1]) / length)
            val cross = xAxis.x * (anchor3[1] - origin[1]) - xAxis.y * (anchor3[0] - origin[0])
            val side = if (cross > 0) 1.0 else -1.0 // Anchor 3 is on the positive y side
            return TrueAnchorFrame(origin, xAxis, SpeedControl2D(-xAxis.y * side, xAxis.x * side), anchor2, anchor3)
        }
    }
}
