package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.and
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.primitive.HalfPlane
import kotlin.math.abs

/**
 * Represents a 2D Signed Distance Field (SDF) of a disk cut by a horizontal line, keeping the part above it:
 * a disk intersected with the half plane above the line.
 *
 * @param center The (X, Y) coordinates of the disk's center.
 * @param radius The radius of the disk.
 * @param cutHeight The height of the cutting line above [center], between `-radius` (nothing is cut)
 * and [radius] (nothing is left).
 */
class CutDisk(center: Position, radius: Double, cutHeight: Double) : SDF by (
    run {
        require(radius > 0.0) { "Cut disk radius must be positive, got $radius" }
        require(abs(cutHeight) <= radius) { "The cut must be within the disk: |$cutHeight| > $radius" }
        val cut = Position(center.x, center.y + cutHeight)
        Circle(center, radius) and HalfPlane(cut, Position(cut.x + 1.0, cut.y))
    }
)
