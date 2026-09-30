package it.unibo.collektive.sdf.text

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.or
import kotlin.math.PI

/** Renders this character as an SDF, case-insensitively. */
fun Char.toSdf(start: Position, height: Double, thickness: Double = 0.0): SDF {
    require(uppercaseChar() in TextBlock.supportedLetters) { "No SDF glyph is available for '$this'" }
    return toString().toSdf(start, height, thickness)
}

/** Renders this string as an SDF [TextBlock], supporting spaces and newlines. */
fun String.toSdf(
    start: Position,
    height: Double,
    thickness: Double = 0.0,
    spacing: Double = DEFAULT_SPACING * height,
    lineSpacing: Double = DEFAULT_SPACING * height,
): TextBlock = TextBlock(this, start, height, thickness, spacing, lineSpacing)

/**
 * The shapes of the letters, composed from the guides and primitives of a [GlyphScope].
 *
 * The proportions that are specific to a letter live in a private object named after it (e.g. [W.PEAK]),
 * in glyph-local units: the glyph height is 1.
 */
internal object Alphabet {
    /* Directions and sweeps of the arcs, in radians (counterclockwise from +x). */
    private const val RIGHTWARDS = 0.0
    private const val LEFTWARDS = PI
    private const val DOWNWARDS = -PI / 2
    private const val HALF_TURN = PI
    private const val THREE_QUARTER_TURN = 3 * PI / 2

    private object A {
        /** Height of the crossbar, as a fraction of the legs. */
        const val CROSSBAR = 0.4
    }

    private object B {
        /** How much narrower the upper bowl is than the lower one. */
        const val UPPER_BOWL_INSET = 0.125
    }

    private object C {
        /** Half of the height of the opening, around the middle. */
        const val OPENING = 0.2
    }

    private object E {
        /** Length of the middle arm (shared with F), as a fraction of the glyph width. */
        const val MIDDLE_ARM = 0.75
    }

    private object G {
        /** Top of the opening, which starts from the middle. */
        const val OPENING_TOP = 0.72

        /** Where the inner bar starts. */
        const val BAR_START = 0.28
    }

    private object K {
        /** Height where the arms meet the stem. */
        const val JOINT = 0.4

        /** Where the lower leg leaves the upper arm, as a fraction of the arm. */
        const val LEG_JOINT = 0.3
    }

    private object M {
        /** Height of the central valley. */
        const val VALLEY = 0.45
    }

    private object Q {
        /** Horizontal coordinate where the tail starts, inside the bowl. */
        const val TAIL_X = 0.28

        /** Vertical coordinate where the tail starts, inside the bowl. */
        const val TAIL_Y = 0.22
    }

    private object R {
        /** Where the leg leaves the bowl. */
        const val LEG_START = 0.2
    }

    private object W {
        /** Distance of the two bottom vertices from the sides, as a fraction of the glyph width. */
        const val VALLEY_INSET = 0.22

        /** Height of the central peak. */
        const val PEAK = 0.7
    }

    /* Parts shared by several letters. */

    /** The vertical stroke on the left side. */
    private val GlyphScope.stem: SDF get() = vertical(left)

    /** Height where the [cup] meets the vertical strokes of J and U. */
    private val GlyphScope.cupRim: Double get() = bottom + halfWidth

    /** The semicircle at the bottom of J and U. */
    private val GlyphScope.cup: SDF
        get() = arc(center at cupRim, halfWidth, startAngle = LEFTWARDS, aperture = HALF_TURN)

    /** The stem with the upper bowl, which R extends with a leg. */
    private val GlyphScope.pShape: SDF get() = stem or bowl(middle, top)

    /** The top and the stem with the middle arm, which E extends with a bottom arm. */
    private val GlyphScope.fShape: SDF get() =
        polyline(right at top, left at top, left at bottom) or horizontal(middle, to = E.MIDDLE_ARM * width)

    /** The glyph of each supported letter. */
    val glyphs: Map<Char, Glyph> = mapOf(
        'A' to glyph {
            val apex = center at top
            polyline(left at bottom, apex, right at bottom) or
                line(between(left at bottom, apex, A.CROSSBAR), between(right at bottom, apex, A.CROSSBAR))
        },
        'B' to glyph {
            val lowerBowl = left + radius at bottom + radius
            val upperBowl = left + radius - B.UPPER_BOWL_INSET at top - radius
            stem or
                arc(lowerBowl, radius, startAngle = DOWNWARDS, aperture = HALF_TURN) or
                arc(upperBowl, radius, startAngle = DOWNWARDS, aperture = HALF_TURN) or
                horizontal(bottom, to = lowerBowl.x) or
                horizontal(middle, to = lowerBowl.x) or
                horizontal(top, to = upperBowl.x)
        },
        'C' to glyph { openOval(middle - C.OPENING, middle + C.OPENING) },
        'D' to glyph { bowl(bottom, top) },
        'E' to glyph { fShape or horizontal(bottom) },
        'F' to glyph { fShape },
        'G' to glyph { openOval(middle, G.OPENING_TOP) or horizontal(middle, from = G.BAR_START) },
        'H' to glyph { stem or vertical(right) or horizontal(middle) },
        'I' to glyph { vertical(center) },
        'J' to glyph { cup or vertical(right, from = cupRim) or horizontal(top) },
        'K' to glyph {
            val joint = left at K.JOINT
            stem or line(joint, right at top) or line(between(joint, right at top, K.LEG_JOINT), right at bottom)
        },
        'L' to glyph { polyline(left at top, left at bottom, right at bottom) },
        'M' to glyph { polyline(left at bottom, left at top, center at M.VALLEY, right at top, right at bottom) },
        'N' to glyph { polyline(left at bottom, left at top, right at bottom, right at top) },
        'O' to glyph { oval() },
        'P' to glyph { pShape },
        'Q' to glyph { oval() or line(Q.TAIL_X at Q.TAIL_Y, right at bottom) },
        'R' to glyph { pShape or line(R.LEG_START at middle, right at bottom) },
        'S' to glyph {
            arc(center at top - halfWidth, halfWidth, startAngle = RIGHTWARDS, aperture = THREE_QUARTER_TURN) or
                arc(center at bottom + halfWidth, halfWidth, startAngle = LEFTWARDS, aperture = THREE_QUARTER_TURN)
        },
        'T' to glyph { horizontal(top) or vertical(center) },
        'U' to glyph { cup or vertical(left, from = cupRim) or vertical(right, from = cupRim) },
        'V' to glyph { polyline(left at top, center at bottom, right at top) },
        'W' to glyph(width = WIDE_GLYPH_WIDTH) {
            val inset = W.VALLEY_INSET * width
            polyline(left at top, left + inset at bottom, center at W.PEAK, right - inset at bottom, right at top)
        },
        'X' to glyph { line(left at bottom, right at top) or line(left at top, right at bottom) },
        'Y' to glyph { polyline(left at top, center at middle, right at top) or vertical(center, to = middle) },
        'Z' to glyph { polyline(left at top, right at top, left at bottom, right at bottom) },
    )
}
