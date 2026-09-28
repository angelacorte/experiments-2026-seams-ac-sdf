package it.unibo.collektive.localization

import it.unibo.collektive.model.Position
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The frame fixed by three anchors that know only their mutual distances, as in Čapkun et al.: anchor 1 at the origin,
 * anchor 2 on the positive x-axis, and anchor 3 on the positive y side (distances alone cannot tell a frame from its
 * mirror image).
 *
 * @property anchor1ToAnchor2 the distance between anchors 1 and 2.
 * @property anchor1ToAnchor3 the distance between anchors 1 and 3.
 * @property anchor2ToAnchor3 the distance between anchors 2 and 3.
 */
data class AnchorFrame(val anchor1ToAnchor2: Double, val anchor1ToAnchor3: Double, val anchor2ToAnchor3: Double) {
    /** The position of anchor 3. */
    val anchor3: Position = run {
        val x = (anchor1ToAnchor3.pow(2) - anchor2ToAnchor3.pow(2) + anchor1ToAnchor2.pow(2)) / (2 * anchor1ToAnchor2)
        Position(x, sqrt(max(0.0, anchor1ToAnchor3.pow(2) - x.pow(2))))
    }

    /** The centroid of the three anchors. */
    val centroid: Position get() = Position((anchor1ToAnchor2 + anchor3.x) / 3, anchor3.y / 3)

    /**
     * Trilaterates the position at [distanceToAnchor1], [distanceToAnchor2] and [distanceToAnchor3] from the anchors;
     * null if the anchors are unknown or collinear.
     */
    fun trilaterate(distanceToAnchor1: Double, distanceToAnchor2: Double, distanceToAnchor3: Double): Position? {
        val usable = anchor1ToAnchor2 > 0.0 && anchor3.y > 0.0 // False with NaN, too.
        val x = (distanceToAnchor1.pow(2) - distanceToAnchor2.pow(2) + anchor1ToAnchor2.pow(2)) / (2 * anchor1ToAnchor2)
        val anchor3Squared = anchor3.x.pow(2) + anchor3.y.pow(2)
        val y = (distanceToAnchor1.pow(2) - distanceToAnchor3.pow(2) + anchor3Squared - 2 * anchor3.x * x) /
            (2 * anchor3.y)
        return Position(x, y).takeIf { usable && x.isFinite() && y.isFinite() }
    }
}
