package it.unibo.collektive.sdf.text

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.expand
import it.unibo.collektive.sdf.primitive.Arc
import it.unibo.collektive.sdf.primitive.RoundedRectangle
import it.unibo.collektive.sdf.primitive.Segment
import it.unibo.collektive.sdf.union

internal const val GLYPH_WIDTH = 0.5
internal const val GLYPH_RADIUS = GLYPH_WIDTH / 2

/** The width of the glyphs that need more room than the standard [GLYPH_WIDTH], such as W. */
internal const val WIDE_GLYPH_WIDTH = 0.75

/** The normalized width of the cell of [character]: [WIDE_GLYPH_WIDTH] for W, [GLYPH_WIDTH] otherwise. */
internal fun glyphWidth(character: Char): Double = when (character.uppercaseChar()) {
    'W' -> WIDE_GLYPH_WIDTH
    else -> GLYPH_WIDTH
}

/** Marks declarations that belong to the glyph-building DSL. */
@DslMarker
private annotation class GlyphDsl

/** A point expressed in glyph-local coordinates, where one unit is the glyph height. */
internal data class GlyphPoint(val x: Double, val y: Double)

/**
 * Builds letter geometry in a normalized coordinate system.
 *
 * The origin is the glyph's bottom-left corner, the y-axis spans `0.0..1.0`, and the glyph spans [width]
 * horizontally. Coordinates are converted to world space only when a primitive is created.
 *
 * @property width The width of this glyph in normalized coordinates ([GLYPH_WIDTH] unless the glyph is wider).
 */
@GlyphDsl
internal class GlyphScope(
    private val origin: Position,
    private val height: Double,
    val width: Double = GLYPH_WIDTH,
) {
    /** The standard radius used by rounded glyph parts. */
    val radius: Double = GLYPH_RADIUS

    /** Creates a glyph-local point, for example `0 at 1` or `0.25 at 0.5`. */
    infix fun Number.at(y: Number): GlyphPoint = GlyphPoint(toDouble(), y.toDouble())

    /** Creates a zero-width line between two glyph-local points. */
    fun line(from: GlyphPoint, to: GlyphPoint): SDF = Segment(from.toWorld(), to.toWorld())

    /** Creates connected zero-width line segments through [points]. */
    fun polyline(vararg points: GlyphPoint): SDF {
        require(points.size >= 2) { "A polyline needs at least two points" }
        return points.asList().zipWithNext(::line).union()
    }

    /** Creates a zero-width circular arc in glyph-local coordinates. */
    fun arc(center: GlyphPoint, radius: Double, startAngle: Double, aperture: Double): SDF =
        Arc(center.toWorld(), radius * height, startAngle, aperture)

    /** Creates a rectangle from normalized horizontal and vertical bounds. */
    fun box(
        horizontal: ClosedFloatingPointRange<Double>,
        vertical: ClosedFloatingPointRange<Double>,
        corners: Corners = Corners(),
    ): SDF {
        require(horizontal.start <= horizontal.endInclusive) { "Horizontal glyph bounds must be ordered" }
        require(vertical.start <= vertical.endInclusive) { "Vertical glyph bounds must be ordered" }
        return RoundedRectangle(
            center = ((horizontal.start + horizontal.endInclusive) / 2 at
                (vertical.start + vertical.endInclusive) / 2).toWorld(),
            width = (horizontal.endInclusive - horizontal.start) * height,
            height = (vertical.endInclusive - vertical.start) * height,
            topLeft = corners.topLeft * height,
            topRight = corners.topRight * height,
            bottomRight = corners.bottomRight * height,
            bottomLeft = corners.bottomLeft * height,
        )
    }

    /** The rounded region whose outline is shared by C, G, and O. */
    fun oval(): SDF = box(
        horizontal = 0.0..width,
        vertical = 0.0..1.0,
        corners = Corners.all(radius),
    )

    /** A D-shaped region, flat on the left and rounded on the right. */
    fun bowl(bottom: Double, top: Double): SDF = box(
        horizontal = 0.0..width,
        vertical = bottom..top,
        corners = Corners(topRight = radius, bottomRight = radius),
    )

    private fun GlyphPoint.toWorld(): Position = Position(origin.x + x * height, origin.y + y * height)
}

/** Corner radii in normalized glyph coordinates. */
internal data class Corners(
    val topLeft: Double = 0.0,
    val topRight: Double = 0.0,
    val bottomRight: Double = 0.0,
    val bottomLeft: Double = 0.0,
) {
    internal companion object {
        fun all(radius: Double): Corners = Corners(radius, radius, radius, radius)
    }
}

/** Builds a glyph and applies [thickness] uniformly to its composed strokes. */
internal fun glyph(
    origin: Position,
    height: Double,
    thickness: Double = 0.0,
    width: Double = GLYPH_WIDTH,
    draw: GlyphScope.() -> SDF,
): SDF {
    require(height > 0.0) { "Glyph height must be positive, got $height" }
    require(thickness >= 0.0) { "Glyph thickness cannot be negative, got $thickness" }
    require(width > 0.0) { "Glyph width must be positive, got $width" }
    return GlyphScope(origin, height, width).draw() expand thickness
}

