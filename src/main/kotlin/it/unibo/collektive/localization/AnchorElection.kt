package it.unibo.collektive.localization

import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.distanceTo
import it.unibo.collektive.stdlib.spreading.gradientCast
import kotlin.Double.Companion.NaN
import kotlin.Double.Companion.POSITIVE_INFINITY
import kotlin.math.abs

/**
 * Elects the anchors fixing the frame from the [neighborDistances] only: anchor 1 is the leader, elected within
 * [leaderElectionBound] hops, and it picks anchors 2 and 3 among all the devices (not only among its neighbors, so
 * that the choice survives an unstable 1-hop neighborhood) as the corners of a triangle with sides of about
 * [anchorsSize] (along the shortest paths), so that the anchors, which stay still, fit in the shape.
 * The candidates reach anchor 1 by a converge-cast (C) along the gradient (G) of the distances from it, and its
 * choice is spread back by a gradient-cast. Anchor 3 must be at least [minAnchorsHeight] away from the line of anchors
 * 1 and 2, so that the frame is not (almost) degenerate; an anchor is dropped (and another chosen) only when anchor 1
 * misses its report for more than [anchorPatience] rounds.
 */
fun Aggregate<Int>.electAnchors(
    neighborDistances: Field<Int, Double>,
    leaderElectionBound: Int,
    minAnchorsHeight: Double,
    anchorsSize: Double,
    anchorPatience: Int,
): AnchorRole {
    val anchor1 = boundedElection(-localId, leaderElectionBound)
    val isAnchor1 = anchor1 == localId
    // Only anchor 1 keeps a choice, updated from the reports of this round.
    return evolving(AnchorChoice()) { previous ->
        val choice = if (isAnchor1) previous else AnchorChoice()
        // G: the choice of anchor 1 reaches every device.
        val chosen = gradientCast(isAnchor1, choice.takeIf { isAnchor1 }, neighborDistances)
        val role = when (localId) {
            anchor1 -> AnchorRole.ANCHOR_1
            chosen?.anchor2 -> AnchorRole.ANCHOR_2
            chosen?.anchor3 -> AnchorRole.ANCHOR_3
            else -> AnchorRole.NONE
        }
        val isAnchor2 = role == AnchorRole.ANCHOR_2
        val toAnchor1 = distanceTo(isAnchor1, neighborDistances)
        val toAnchor2 = distanceTo(isAnchor2, neighborDistances)
        // Anchor 2 spreads its distance from anchor 1, the base of the triangle (NaN until it gets here).
        val anchor1ToAnchor2 = gradientCast(isAnchor2, if (isAnchor2) toAnchor1 else NaN, neighborDistances)
        val height = AnchorFrame(anchor1ToAnchor2, toAnchor1, toAnchor2).anchor3.y.takeIf(Double::isFinite) ?: 0.0
        // How far the device is from the corners of the triangle, anchor 2 on its base and anchor 3 at its top.
        val baseError = abs(toAnchor1 - anchorsSize)
        val topError = (baseError + abs(toAnchor2 - anchorsSize)).takeIf { height > minAnchorsHeight }
        val local = Candidate(localId, baseError, topError ?: POSITIVE_INFINITY, height)
        val candidate = local.takeIf { !isAnchor1 && baseError.isFinite() }
        // C: the candidates, and the current anchors, reach anchor 1 along the gradient of the distances from it.
        val reports = convergeCast(
            local = CandidateReports(
                base = candidate,
                top = candidate?.takeIf { !isAnchor2 && it.topError.isFinite() },
                anchors = if (isAnchor2 || role == AnchorRole.ANCHOR_3) mapOf(localId to local) else emptyMap(),
            ),
            potential = toAnchor1,
            accumulateData = CandidateReports::merge,
        )
        val next = if (isAnchor1) choice.update(reports, minAnchorsHeight, anchorPatience) else choice
        next.yielding { role }
    }
}

/**
 * A device that may become anchor 2 or 3: how far it is from the corner of the base ([baseError]) and from the top
 * ([topError], infinite if too close to the line 1-2) of the triangle of the anchors, and [height] away from the line.
 */
private data class Candidate(val id: Int, val baseError: Double, val topError: Double, val height: Double)

/**
 * What reaches anchor 1: the best candidate for the [base] (anchor 2), the best one for the [top] (anchor 3), and the
 * reports of the current [anchors].
 */
private data class CandidateReports(val base: Candidate?, val top: Candidate?, val anchors: Map<Int, Candidate>) {
    fun merge(other: CandidateReports) = CandidateReports(
        base = listOfNotNull(base, other.base).minByOrNull { it.baseError },
        top = listOfNotNull(top, other.top).minByOrNull { it.topError },
        anchors = anchors + other.anchors,
    )
}

/**
 * The anchors 2 and 3 chosen by anchor 1 (null until chosen), and for how many rounds their reports are [missed2] and
 * [missed3].
 */
private data class AnchorChoice(
    val anchor2: Int? = null,
    val anchor3: Int? = null,
    val missed2: Int = 0,
    val missed3: Int = 0,
) {
    /**
     * Keeps the current anchors while their [reports] keep coming (and anchor 3 stays above [minAnchorsHeight]),
     * tolerating [patience] rounds without them, so that the frame does not move; otherwise, picks the best candidate
     * for the base as anchor 2, and then the best one for the top as anchor 3.
     */
    fun update(reports: CandidateReports, minAnchorsHeight: Double, patience: Int): AnchorChoice {
        val missed2 = if (anchor2 != null && anchor2 in reports.anchors) 0 else missed2 + 1
        if (anchor2 == null || missed2 > patience) {
            return AnchorChoice(anchor2 = reports.base?.id) // A new base: anchor 3 is chosen again.
        }
        val confirmed3 = anchor3 != null && (reports.anchors[anchor3]?.height ?: 0.0) > minAnchorsHeight
        val missed3 = if (confirmed3) 0 else missed3 + 1
        return when {
            anchor3 != null && missed3 <= patience -> copy(missed2 = missed2, missed3 = missed3)
            else -> copy(
                anchor3 = reports.top?.id,
                missed2 = missed2,
                missed3 = 0,
            )
        }
    }
}
