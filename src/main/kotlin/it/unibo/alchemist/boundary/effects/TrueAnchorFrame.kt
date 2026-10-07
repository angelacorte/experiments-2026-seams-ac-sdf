package it.unibo.alchemist.boundary.effects

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
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
         * The anchors 1, 2 and 3 that fix the frame in [environment], null until they are elected. The anchors are
         * told apart by their `anchor` molecule (the name of their `AnchorRole`), as they need not be neighbors.
         * A split network has an anchor 1 in each part (a node cut off from the swarm leads alone): the frame is the
         * one of the part with the most devices, with anchors 2 and 3 the nearest ones to anchor 1 within that part.
         */
        fun <T, P : Position<P>> anchorsIn(environment: Environment<T, P>): List<Node<T>>? {
            fun withRole(role: String) = environment.nodes.filter { it.getConcentration(ANCHOR) == role }
            val parts = withRole("ANCHOR_1").map { it to environment.connectedTo(it) }
            val largest = parts.maxOfOrNull { (_, part) -> part.size }
            return parts.filter { (_, part) -> part.size == largest }.firstNotNullOfOrNull { (anchor1, part) ->
                val others = listOf("ANCHOR_2", "ANCHOR_3").mapNotNull { role ->
                    withRole(role).filter {
                        it in part
                    }.minByOrNull { environment.getDistanceBetweenNodes(anchor1, it) }
                }
                (listOf(anchor1) + others).takeIf { it.size == 3 }
            }
        }

        /**
         * The nodes that [node] reaches in this environment, hop by hop through the neighborhoods (itself included).
         */
        private fun <T, P : Position<P>> Environment<T, P>.connectedTo(node: Node<T>): Set<Node<T>> {
            val reached = mutableSetOf(node)
            val frontier = ArrayDeque(listOf(node))
            while (frontier.isNotEmpty()) {
                for (neighbor in getNeighborhood(frontier.removeFirst()).neighbors) {
                    if (reached.add(neighbor)) frontier.addLast(neighbor)
                }
            }
            return reached
        }

        /** The frame of the anchors in [environment] (see [anchorsIn]), null until they are elected. */
        fun <T, P : Position<P>> of(environment: Environment<T, P>): TrueAnchorFrame? {
            val (origin, anchor2, anchor3) = anchorsIn(environment)
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
