package it.unibo.collektive.sdf.shape

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.polar
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.expand
import it.unibo.collektive.sdf.primitive.Arc
import it.unibo.collektive.sdf.union
import kotlin.math.PI

/**
 * Builds a tangent chain of circular arcs with increasing or decreasing radii.
 * Each arc starts where the previous one ends and continues in the same direction.
 */
internal fun tangentArcChain(
    center: Position,
    radii: List<Double>,
    sweep: Double,
    thickness: Double,
): SDF {
    require(radii.isNotEmpty()) { "An arc chain needs at least one radius" }
    require(radii.all { it >= 0.0 }) { "Arc chain radii cannot be negative, got $radii" }
    require(sweep > 0.0 && sweep <= 2 * PI) { "Arc chain sweep must be in (0, 2π], got $sweep" }
    require(thickness >= 0.0) { "Arc chain thickness cannot be negative, got $thickness" }

    var arcCenter = center
    var startAngle = 0.0
    val chain = radii.mapIndexed { index, radius ->
        if (index > 0) {
            arcCenter = arcCenter.polar(radii[index - 1] - radius, startAngle + sweep)
            startAngle += sweep
        }
        Arc(arcCenter, radius, startAngle, sweep)
    }.union()
    return chain expand thickness
}
