package it.unibo.collektive.localization

import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.toMap
import it.unibo.collektive.stdlib.consensus.boundedElection

/**
 * Elects the anchors fixing the frame from the [neighborDistances] only: anchor 1 is the leader, and it picks
 * anchors 2 and 3 among its own neighbors. No further election is needed, and the distances among the anchors are
 * direct measures that do not change while they stay still. Anchor 1 is elected within [leaderElectionBound] hops, and
 * anchor 3 must be at least [minAnchorsHeight] away from the line of anchors 1 and 2, so that the frame is not
 * (almost) degenerate.
 */
fun Aggregate<Int>.electAnchors(
    neighborDistances: Field<Int, Double>,
    leaderElectionBound: Int,
    minAnchorsHeight: Double,
): AnchorRole {
    val anchor1 = boundedElection(localId, leaderElectionBound)
    // Anchor 1 also needs the distances among its neighbors.
    val ownDistances = neighborDistances.neighbors.toMap() // Read once: the neighbors view is a one-shot sequence.
    val distancesAmongNeighbors = neighboring(ownDistances).neighbors.toMap()
    val anchors2And3 = evolve<Pair<Int, Int>?>(null) { previous ->
        when (localId) {
            anchor1 -> chooseAnchors2And3(previous, ownDistances, distancesAmongNeighbors, minAnchorsHeight)
            else -> null
        }
    }
    val choiceOfAnchor1 = neighboring(anchors2And3).all.toMap()[anchor1]
    return when (localId) {
        anchor1 -> AnchorRole.ANCHOR_1
        choiceOfAnchor1?.first -> AnchorRole.ANCHOR_2
        choiceOfAnchor1?.second -> AnchorRole.ANCHOR_3
        else -> AnchorRole.NONE
    }
}

/**
 * Anchor 1 picks anchors 2 and 3 among its neighbors, as in Čapkun et al.:
 * the farthest neighbor as anchor 2, and the common neighbor making the tallest triangle as anchor 3.
 * It keeps its [previous] choice while it is still valid, so the frame does not move;
 * its own [distances] and the [distancesAmongNeighbors] are all direct (1-hop) measures.
 * A choice is valid if anchor 3 is at least [minAnchorsHeight] away from the line of anchors 1 and 2.
 */
private fun chooseAnchors2And3(
    previous: Pair<Int, Int>?,
    distances: Map<Int, Double>,
    distancesAmongNeighbors: Map<Int, Map<Int, Double>>,
    minAnchorsHeight: Double,
): Pair<Int, Int>? {
    fun height(anchors: Pair<Int, Int>): Double {
        val anchor1ToAnchor2 = distances[anchors.first]
        val anchor1ToAnchor3 = distances[anchors.second]
        val anchor2ToAnchor3 = distancesAmongNeighbors[anchors.first]?.get(anchors.second)
        val height = when {
            anchor1ToAnchor2 == null || anchor1ToAnchor3 == null || anchor2ToAnchor3 == null -> 0.0 // Not neighbors.
            else -> AnchorFrame(anchor1ToAnchor2, anchor1ToAnchor3, anchor2ToAnchor3).anchor3.y
        }
        return height.takeIf(Double::isFinite) ?: 0.0
    }
    val best = distances.maxByOrNull { it.value }?.key?.let { anchor2 ->
        distances.keys.filter { it != anchor2 }.map { anchor2 to it }.maxByOrNull(::height)
    }
    return listOfNotNull(previous, best).firstOrNull { height(it) > minAnchorsHeight }
}
