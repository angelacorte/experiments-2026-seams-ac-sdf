package it.unibo.collektive.localization

import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.stdlib.spreading.distanceTo
import it.unibo.collektive.stdlib.spreading.gradientCast
import kotlin.Double.Companion.NaN

/**
 * Where a device is in the [frame] fixed by the anchors.
 *
 * @property frame the frame fixed by the anchors.
 * @property position the estimated position in the [frame], null until the frame is known.
 */
data class Localization(val frame: AnchorFrame, val position: Position?)

/**
 * Range-only localization (DV-distance, as in the APS of Niculescu & Nath): given its [role], every device measures
 * its path distances to the three anchors over the [neighborDistances], the anchors broadcast their mutual distances,
 * and the device trilaterates its position in the frame they fix.
 */
fun Aggregate<Int>.localize(role: AnchorRole, neighborDistances: Field<Int, Double>): Localization {
    val isAnchor2 = role == AnchorRole.ANCHOR_2
    val isAnchor3 = role == AnchorRole.ANCHOR_3
    val distanceToAnchor1 = distanceTo(role == AnchorRole.ANCHOR_1, neighborDistances)
    val distanceToAnchor2 = distanceTo(isAnchor2, neighborDistances)
    val distanceToAnchor3 = distanceTo(isAnchor3, neighborDistances)
    // The anchors broadcast their mutual distances (NaN until they get here).
    val anchor1ToAnchor2 = gradientCast(isAnchor2, if (isAnchor2) distanceToAnchor1 else NaN, neighborDistances)
    val (anchor1ToAnchor3, anchor2ToAnchor3) = gradientCast(
        isAnchor3,
        if (isAnchor3) distanceToAnchor1 to distanceToAnchor2 else NaN to NaN,
        neighborDistances,
    )
    val frame = AnchorFrame(anchor1ToAnchor2, anchor1ToAnchor3, anchor2ToAnchor3)
    return Localization(frame, frame.trilaterate(distanceToAnchor1, distanceToAnchor2, distanceToAnchor3))
}
