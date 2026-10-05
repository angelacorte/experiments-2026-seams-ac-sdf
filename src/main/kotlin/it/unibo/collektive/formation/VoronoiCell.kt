package it.unibo.collektive.formation

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.geometry.dot
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.geometry.zeroSpeed
import it.unibo.collektive.sdf.SDF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DISK_SIDES = 32 // Sides of the polygon that bounds the cell before the bisectors cut it

private const val SAMPLES = 8 // Samples per side of the box of the cell

/**
 * The centroid of the Voronoi cell of a device at [position] among its [neighbors] (where they are, seen from it),
 * as an offset from the device: the points of the [shape] closer to the device than to any neighbor, within [reach]
 * (the limited-range cell of Cortés et al.). Out of the shape, the whole cell instead: on its way to the shape (which
 * pulls it in anyway), the device still keeps clear of its neighbors.
 * Each sample counts as much of its pixel as lies in the cell and in the shape, from its distance to their borders
 * (anti-aliasing, as when rendering an SDF): the centroid then moves smoothly with the device, instead of jumping as
 * samples cross the border, which makes the devices flicker once in place.
 */
fun voronoiCentroid(shape: SDF, position: Position, neighbors: List<Vector2D>, reach: Double): SpeedControl2D {
    val disk = Cell(
        (0 until DISK_SIDES).map {
            SpeedControl2D(reach * cos(2 * PI * it / DISK_SIDES), reach * sin(2 * PI * it / DISK_SIDES))
        },
    )
    // From the nearest: once a bisector misses the cell, the farther ones miss it too
    val cell = neighbors.filter { it.norm > 0.0 }.sortedBy { it.norm }.fold(disk, Cell::cutBy)
    return if (cell.corners.isEmpty()) zeroSpeed else cell.centroidIn(shape, position, reach)
}

/** The anti-aliased centroid of this (non-empty) cell of a device at [position], in [shape] (see [voronoiCentroid]). */
private fun Cell.centroidIn(shape: SDF, position: Position, reach: Double): SpeedControl2D {
    val minX = corners.minOf { it.x }
    val minY = corners.minOf { it.y }
    val stepX = (corners.maxOf { it.x } - minX) / SAMPLES
    val stepY = (corners.maxOf { it.y } - minY) / SAMPLES
    val pixel = maxOf(stepX, stepY).takeIf { it > 0.0 } ?: return zeroSpeed
    val outside = shape(position) > 0.0
    val weighted = (0 until SAMPLES * SAMPLES)
        .map { SpeedControl2D(minX + (it / SAMPLES + 0.5) * stepX, minY + (it % SAMPLES + 0.5) * stepY) }
        .mapNotNull { point ->
            val inCell = depthOf(point, reach)
            // A pixel out of the cell spares the SDF
            inCell.takeIf { it > -pixel / 2 }?.let {
                val depth = if (outside) inCell else minOf(inCell, -shape(position + point))
                point to (0.5 + depth / pixel).coerceIn(0.0, 1.0)
            }
        }
    val weight = weighted.sumOf { (_, coverage) -> coverage }
    return when (weight) {
        0.0 -> zeroSpeed
        else -> SpeedControl2D(
            weighted.sumOf { (point, coverage) -> point.x * coverage } / weight,
            weighted.sumOf { (point, coverage) -> point.y * coverage } / weight,
        )
    }
}

/** A convex cell around the device (at the origin), with its [corners], and the neighbors [cutting] it. */
private class Cell(val corners: List<SpeedControl2D>, private val cutting: List<Vector2D> = emptyList()) {
    /** The distance of the farthest corner: the bisector of a neighbor more than twice as far misses the cell. */
    private val radius = corners.maxOfOrNull { it.norm } ?: 0.0

    /** This cell, cut by the bisector between the device and [neighbor], if it reaches the cell. */
    fun cutBy(neighbor: Vector2D): Cell = when {
        neighbor.norm / 2 > radius -> this
        else -> Cell(corners.cutBy(neighbor), cutting + neighbor)
    }

    /** How deep [point] lies in the cell: its distance from the disk of [reach] and from every bisector. */
    fun depthOf(point: Vector2D, reach: Double): Double = cutting.fold(reach - point.norm) { depth, neighbor ->
        minOf(depth, ((neighbor dot neighbor) / 2 - (point dot neighbor)) / neighbor.norm)
    }
}

/** This convex polygon, cut by the bisector between the device (at the origin) and [neighbor]: its side is kept. */
private fun List<SpeedControl2D>.cutBy(neighbor: Vector2D): List<SpeedControl2D> {
    val half = (neighbor dot neighbor) / 2
    fun margin(point: Vector2D) = half - (point dot neighbor) // Not negative on the side of the device
    return indices.flatMap { i ->
        val from = this[i]
        val to = this[(i + 1) % size]
        val (marginFrom, marginTo) = margin(from) to margin(to)
        listOfNotNull(
            from.takeIf { marginFrom >= 0.0 },
            (from + (to - from) * (marginFrom / (marginFrom - marginTo))).takeIf { marginFrom * marginTo < 0.0 },
        )
    }
}
