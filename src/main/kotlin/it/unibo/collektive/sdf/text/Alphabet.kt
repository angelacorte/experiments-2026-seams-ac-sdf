package it.unibo.collektive.sdf.text

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.minus
import it.unibo.collektive.sdf.or
import it.unibo.collektive.sdf.outline
import kotlin.math.PI

/** The uppercase Latin characters supported by the SDF alphabet. */
internal val LATIN_ALPHABET: CharRange = 'A'..'Z'

/** Renders this character as an SDF, case-insensitively. */
fun Char.toSdf(start: Position, height: Double, thickness: Double = 0.0): SDF {
    val letter = uppercaseChar()
    require(letter in LATIN_ALPHABET) { "No SDF glyph is available for '$this'" }
    return glyph(start, height, thickness, glyphWidth(letter)) { Alphabet.draw(this, letter) }
}

/** Renders this string as an SDF [TextBlock], supporting spaces and newlines. */
fun String.toSdf(
    start: Position,
    height: Double,
    thickness: Double = 0.0,
    spacing: Double = height / 4,
    lineSpacing: Double = height / 4,
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

    /** The vertical stroke on the left side, shared by many letters. */
    private val GlyphScope.stem: SDF get() = line(left at bottom, left at top)

    /** Describes [letter] in [scope], by composing normalized SDF primitives. */
    fun draw(scope: GlyphScope, letter: Char): SDF = with(scope) {
        when (letter) {
            'A' -> {
                val apex = center at top
                polyline(left at bottom, apex, right at bottom) or
                    line(between(left at bottom, apex, A.CROSSBAR), between(right at bottom, apex, A.CROSSBAR))
            }

            'B' -> {
                val lowerBowl = left + radius at bottom + radius
                val upperBowl = left + radius - B.UPPER_BOWL_INSET at top - radius
                stem or
                    arc(lowerBowl, radius, startAngle = DOWNWARDS, aperture = HALF_TURN) or
                    arc(upperBowl, radius, startAngle = DOWNWARDS, aperture = HALF_TURN) or
                    line(left at bottom, lowerBowl.x at bottom) or
                    line(left at middle, lowerBowl.x at middle) or
                    line(left at top, upperBowl.x at top)
            }

            'C' -> oval().outline() - box(center..beyondRight, middle - C.OPENING..middle + C.OPENING)
            'D' -> bowl(bottom, top).outline()
            'E' ->
                polyline(right at top, left at top, left at bottom, right at bottom) or
                    line(left at middle, E.MIDDLE_ARM * width at middle)

            'F' ->
                polyline(right at top, left at top, left at bottom) or
                    line(left at middle, E.MIDDLE_ARM * width at middle)

            'G' ->
                (oval().outline() - box(center..beyondRight, middle..G.OPENING_TOP)) or
                    line(G.BAR_START at middle, right at middle)

            'H' -> stem or line(right at bottom, right at top) or line(left at middle, right at middle)
            'I' -> line(center at bottom, center at top)
            'J' -> {
                val hook = center at bottom + width / 2
                arc(hook, width / 2, startAngle = LEFTWARDS, aperture = HALF_TURN) or
                    line(right at hook.y, right at top) or
                    line(left at top, right at top)
            }

            'K' -> {
                val joint = left at K.JOINT
                stem or line(joint, right at top) or line(between(joint, right at top, K.LEG_JOINT), right at bottom)
            }

            'L' -> polyline(left at top, left at bottom, right at bottom)
            'M' -> polyline(left at bottom, left at top, center at M.VALLEY, right at top, right at bottom)
            'N' -> polyline(left at bottom, left at top, right at bottom, right at top)
            'O' -> oval().outline()
            'P' -> stem or bowl(middle, top).outline()
            'Q' -> oval().outline() or line(Q.TAIL_X at Q.TAIL_Y, right at bottom)
            'R' -> stem or bowl(middle, top).outline() or line(R.LEG_START at middle, right at bottom)
            'S' ->
                arc(center at top - width / 2, width / 2, startAngle = RIGHTWARDS, aperture = THREE_QUARTER_TURN) or
                    arc(center at bottom + width / 2, width / 2, startAngle = LEFTWARDS, aperture = THREE_QUARTER_TURN)

            'T' -> line(left at top, right at top) or line(center at bottom, center at top)
            'U' -> {
                val cup = center at bottom + width / 2
                line(left at cup.y, left at top) or
                    line(right at cup.y, right at top) or
                    arc(cup, width / 2, startAngle = LEFTWARDS, aperture = HALF_TURN)
            }

            'V' -> polyline(left at top, center at bottom, right at top)
            'W' -> {
                val inset = W.VALLEY_INSET * width
                polyline(left at top, left + inset at bottom, center at W.PEAK, right - inset at bottom, right at top)
            }

            'X' -> line(left at bottom, right at top) or line(left at top, right at bottom)
            'Y' -> polyline(left at top, center at middle, right at top) or line(center at bottom, center at middle)
            'Z' -> polyline(left at top, right at top, left at bottom, right at bottom)
            else -> error("Unsupported SDF glyph '$letter'")
        }
    }
}
