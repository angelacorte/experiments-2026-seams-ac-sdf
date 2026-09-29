@file:Suppress("MagicNumber")

package it.unibo.collektive.sdf.impl.letters

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
    return glyph(start, height, thickness) { draw(letter) }
}

/** Renders this string as an SDF [Word]. */
fun String.toSdf(
    start: Position,
    height: Double,
    thickness: Double = 0.0,
    spacing: Double = height / 4,
): Word = Word(this, start, height, thickness, spacing)

/** Describes each glyph by composing normalized SDF primitives. */
private fun GlyphScope.draw(letter: Char): SDF = when (letter) {
    'A' ->
        line(0 at 0, width / 2 at 1) or
            line(width / 2 at 1, width at 0) or
            line(0.1 at 0.4, 0.4 at 0.4)

    'B' ->
        line(0 at 0, 0 at 1) or
            arc(0.25 at 0.25, radius = 0.25, startAngle = -PI / 2, aperture = PI) or
            arc(0.125 at 0.75, radius = 0.25, startAngle = -PI / 2, aperture = PI) or
            line(0 at 0, 0.25 at 0) or
            line(0 at 0.5, 0.25 at 0.5) or
            line(0 at 1, 0.125 at 1)

    'C' -> oval().outline() - box(radius..1.0, 0.3..0.7)
    'D' -> bowl(0.0, 1.0).outline()
    'E' ->
        line(0 at 0, 0 at 1) or
            line(0 at 0, width at 0) or
            line(0 at 0.5, 0.375 at 0.5) or
            line(0 at 1, width at 1)

    'F' ->
        line(0 at 0, 0 at 1) or
            line(0 at 1, width at 1) or
            line(0 at 0.5, 0.375 at 0.5)

    'G' -> (oval().outline() - box(radius..1.0, 0.5..0.72)) or line(0.28 at 0.5, width at 0.5)
    'H' ->
        line(0 at 0, 0 at 1) or
            line(width at 0, width at 1) or
            line(0 at 0.5, width at 0.5)

    'I' -> line(width / 2 at 0, width / 2 at 1)
    'J' ->
        line(width at 0.25, width at 1) or
            line(0 at 1, width at 1) or
            arc(width / 2 at 0.25, radius = width / 2, startAngle = PI, aperture = PI)

    'K' ->
        line(0 at 0, 0 at 1) or
            line(0 at 0.4, width at 1) or
            line(0.15 at 0.58, width at 0)

    'L' -> line(0 at 0, 0 at 1) or line(0 at 0, width at 0)
    'M' -> polyline(0 at 0, 0 at 1, width / 2 at 0.45, width at 1, width at 0)
    'N' -> polyline(0 at 0, 0 at 1, width at 0, width at 1)
    'O' -> oval().outline()
    'P' -> line(0 at 0, 0 at 1) or bowl(0.5, 1.0).outline()
    'Q' -> oval().outline() or line(0.28 at 0.22, width at 0)
    'R' ->
        line(0 at 0, 0 at 1) or
            bowl(0.5, 1.0).outline() or
            line(0.2 at 0.5, width at 0)

    'S' ->
        arc(width / 2 at 0.75, radius = width / 2, startAngle = 0.0, aperture = 3 * PI / 2) or
            arc(width / 2 at 0.25, radius = width / 2, startAngle = PI, aperture = 3 * PI / 2)

    'T' -> line(0 at 1, width at 1) or line(width / 2 at 0, width / 2 at 1)
    'U' ->
        line(0 at 0.25, 0 at 1) or
            line(width at 0.25, width at 1) or
            arc(width / 2 at 0.25, radius = width / 2, startAngle = PI, aperture = PI)

    'V' -> line(0 at 1, width / 2 at 0) or line(width / 2 at 0, width at 1)
    'W' -> polyline(0 at 1, 0.125 at 0, width / 2 at 0.45, 0.375 at 0, width at 1)
    'X' -> line(0 at 0, width at 1) or line(0 at 1, width at 0)
    'Y' ->
        line(0 at 1, width / 2 at 0.5) or
            line(width at 1, width / 2 at 0.5) or
            line(width / 2 at 0, width / 2 at 0.5)

    'Z' -> polyline(0 at 1, width at 1, 0 at 0, width at 0)
    else -> error("Unsupported SDF glyph '$letter'")
}
