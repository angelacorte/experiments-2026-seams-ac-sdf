package it.unibo.collektive.sdf.impl.letters

import it.unibo.collektive.model.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.union

/**
 * Represents a 2D Signed Distance Field (SDF) of a word: its letters side by side along +x, joined in a union.
 * Letters are case-insensitive, and spaces leave a gap as wide as a letter.
 *
 * @param text The word to write, made of the letters in [supportedLetters] and spaces.
 * @param start The (X, Y) coordinates of the bottom-left corner of the first letter.
 * @param height The height of the letters.
 * @param thickness The thickness of the letters' strokes (default is 0.0).
 * @param spacing The empty space between the strokes of two consecutive letters (default is a quarter of [height]).
 */
class Word(
    text: String,
    start: Position,
    height: Double,
    thickness: Double = 0.0,
    spacing: Double = height / 4,
) : SDF {
    private val letters = text.uppercase()

    init {
        require(letters.isNotBlank()) { "A word needs at least one letter" }
        val unsupported = letters.filterNot { it == ' ' || it in LATIN_ALPHABET }
        require(unsupported.isEmpty()) { "No SDF for '$unsupported': supported letters are $supportedLetters" }
    }

    private val shape: SDF = letters
        .runningFold(start.x) { x, _ -> x + advance(height, thickness, spacing) }
        .zip(letters.asIterable())
        .mapNotNull { (x, letter) ->
            letter.takeIf { it in LATIN_ALPHABET }?.toSdf(Position(x, start.y), height, thickness)
        }
        .union()

    /** The width of the word, from the leftmost to the rightmost edge of its strokes. */
    val width: Double = widthOf(letters, height, thickness, spacing)

    override fun invoke(position: Position): Double = shape(position)

    /** The letters a [Word] can be written with. */
    companion object {
        /** The letters that can be written. */
        val supportedLetters: Set<Char> = LATIN_ALPHABET.toSet()

        /** A [Word] centered on [center], instead of starting from its bottom-left corner. */
        fun centeredAt(
            center: Position,
            text: String,
            height: Double,
            thickness: Double = 0.0,
            spacing: Double = height / 4,
        ): Word {
            // The strokes of the first letter stick out of its start by the thickness.
            val start = Position(
                center.x - widthOf(text.uppercase(), height, thickness, spacing) / 2 + thickness,
                center.y - height / 2,
            )
            return Word(text, start, height, thickness, spacing)
        }

        /** How far the next letter starts: its width, the thickness of its strokes, and the spacing. */
        private fun advance(height: Double, thickness: Double, spacing: Double): Double =
            GLYPH_WIDTH * height + 2 * thickness + spacing

        private fun widthOf(letters: String, height: Double, thickness: Double, spacing: Double): Double =
            letters.length * advance(height, thickness, spacing) - spacing
    }
}
