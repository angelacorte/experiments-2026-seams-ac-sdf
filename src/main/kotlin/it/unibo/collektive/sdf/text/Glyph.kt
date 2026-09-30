package it.unibo.collektive.sdf.text

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.minus
import it.unibo.collektive.geometry.plus
import it.unibo.collektive.geometry.times
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.minus
import it.unibo.collektive.sdf.outline
import it.unibo.collektive.sdf.primitive.Arc
import it.unibo.collektive.sdf.primitive.Corners
import it.unibo.collektive.sdf.primitive.RoundedRectangle
import it.unibo.collektive.sdf.primitive.Segment
import it.unibo.collektive.sdf.union

internal const val GLYPH_WIDTH = 0.5
internal const val GLYPH_RADIUS = GLYPH_WIDTH / 2

/** The width of the glyphs that need more room than the standard [GLYPH_WIDTH], such as W. */
internal const val WIDE_GLYPH_WIDTH = 0.75

/**
 * A letter, drawn once in normalized coordinates (the glyph height is 1) with zero-width strokes:
 * a [TextBlock] places, thickens, and scales it.
 *
 * @property width The width of the cell of this glyph, in glyph heights.
 * @property shape The strokes of this glyph, with the origin in its bottom-left corner.
 */
internal class Glyph(val width: Double, val shape: SDF) {
    init {
        require(width > 0.0) { "Glyph width must be positive, got $width" }
    }
}

/** Draws a [Glyph] of the given [width] with the [GlyphScope] DSL. */
internal fun glyph(width: Double = GLYPH_WIDTH, draw: GlyphScope.() -> SDF): Glyph =
    Glyph(width, GlyphScope(width).draw())

/** Marks declarations that belong to the glyph-building DSL. */
@DslMarker
private annotation class GlyphDsl

/**
 * Builds letter geometry in a normalized coordinate system.
 *
 * The origin is the glyph's bottom-left corner, the y-axis spans `0.0..1.0`, and the glyph spans [width]
 * horizontally. Glyphs are drawn once in these coordinates: [TextBlock] scales them to the requested height.
 *
 * @property width The width of this glyph in normalized coordinates ([GLYPH_WIDTH] unless the glyph is wider).
 */
@GlyphDsl
internal class GlyphScope(val width: Double = GLYPH_WIDTH) {
    /** The standard radius used by rounded glyph parts. */
    val radius: Double = GLYPH_RADIUS

    /*
     * The guides of the glyph, to be combined into points with `at`, e.g. `left at top` or `center at middle`.
     * Vertically: the baseline, the middle, and the top. Horizontally: the sides, the axis, and a coordinate
     * safely past the right side, for boxes carving openings through it.
     */
    val bottom = 0.0
    val top = 1.0
    val middle = (bottom + top) / 2
    val left = 0.0
    val right = left + width
    val center = (left + right) / 2
    val halfWidth = width / 2
    val beyondRight = right + width

    /** Creates a glyph-local point, for example `left at top` or `0.25 at middle`. */
    infix fun Double.at(y: Double): Position = Position(this, y)

    /** The point at [fraction] of the way from [from] to [to]: 0 is [from], 1 is [to]. */
    fun between(from: Position, to: Position, fraction: Double): Position = from + (to - from) * fraction

    /** Creates a zero-width line between two glyph-local points. */
    fun line(from: Position, to: Position): SDF = Segment(from, to)

    /** Creates a zero-width vertical line at [x], from [from] upwards to [to] (by default, all of it). */
    fun vertical(x: Double, from: Double = bottom, to: Double = top): SDF = line(x at from, x at to)

    /** Creates a zero-width horizontal line at [y], from [from] rightwards to [to] (by default, all of it). */
    fun horizontal(y: Double, from: Double = left, to: Double = right): SDF = line(from at y, to at y)

    /** Creates connected zero-width line segments through [points]. */
    fun polyline(vararg points: Position): SDF {
        require(points.size >= 2) { "A polyline needs at least two points" }
        return points.asList().zipWithNext(::line).union()
    }

    /** Creates a zero-width circular arc in glyph-local coordinates. */
    fun arc(center: Position, radius: Double, startAngle: Double, aperture: Double): SDF =
        Arc(center, radius, startAngle, aperture)

    /** Creates a rectangle from normalized horizontal and vertical bounds. */
    fun box(
        horizontal: ClosedFloatingPointRange<Double>,
        vertical: ClosedFloatingPointRange<Double>,
        corners: Corners = Corners(),
    ): SDF {
        require(horizontal.start <= horizontal.endInclusive) { "Horizontal glyph bounds must be ordered" }
        require(vertical.start <= vertical.endInclusive) { "Vertical glyph bounds must be ordered" }
        return RoundedRectangle(
            center = (horizontal.start + horizontal.endInclusive) / 2 at (vertical.start + vertical.endInclusive) / 2,
            width = horizontal.endInclusive - horizontal.start,
            height = vertical.endInclusive - vertical.start,
            corners = corners,
        )
    }

    /** The outline of the rounded region shared by C, G, O, and Q. */
    fun oval(): SDF = box(horizontal = left..right, vertical = bottom..top, corners = Corners.all(radius)).outline()

    /** The [oval] with an opening on the right side, between the heights [from] and [to] (C and G). */
    fun openOval(from: Double, to: Double): SDF = oval() - box(center..beyondRight, from..to)

    /** The outline of a D-shaped region, flat on the left and rounded on the right (D, P, and R). */
    fun bowl(bottom: Double, top: Double): SDF = box(
        horizontal = left..right,
        vertical = bottom..top,
        corners = Corners(topRight = radius, bottomRight = radius),
    ).outline()
}
