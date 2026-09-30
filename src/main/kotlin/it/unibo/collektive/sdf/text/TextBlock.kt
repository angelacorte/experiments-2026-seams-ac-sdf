package it.unibo.collektive.sdf.text

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.union

/**
 * A block of SDF text laid out from [start].
 *
 * Letters are case-insensitive, spaces advance horizontally without drawing a glyph, and newlines start a new
 * line below the previous one. Empty lines are preserved.
 *
 * @param text Text made of the letters in [supportedLetters], spaces, and newlines.
 * @param start Glyph origin of the first character in the first line.
 * @param glyphHeight Height of each letter.
 * @param thickness Thickness applied to the letters' strokes.
 * @param spacing Empty horizontal space between consecutive character cells.
 * @param lineSpacing Empty vertical space between consecutive lines.
 */
class TextBlock(
    text: String,
    start: Position,
    glyphHeight: Double,
    thickness: Double = 0.0,
    spacing: Double = glyphHeight / 4,
    lineSpacing: Double = glyphHeight / 4,
) : SDF {
    private val lines = normalizedLines(text)

    init {
        require(glyphHeight > 0.0) { "Glyph height must be positive, got $glyphHeight" }
        require(thickness >= 0.0) { "Glyph thickness cannot be negative, got $thickness" }
        require(spacing >= 0.0) { "Character spacing cannot be negative, got $spacing" }
        require(lineSpacing >= 0.0) { "Line spacing cannot be negative, got $lineSpacing" }
        require(lines.any { line -> line.any { it in LATIN_ALPHABET } }) {
            "A text block needs at least one letter"
        }
        val unsupported = lines
            .asSequence()
            .flatMap { it.asSequence() }
            .filterNot { it == ' ' || it in LATIN_ALPHABET }
            .toSet()
        require(unsupported.isEmpty()) {
            "No SDF glyph for $unsupported: supported letters are $supportedLetters, spaces, and newlines"
        }
    }

    private val lineAdvance = glyphHeight + 2 * thickness + lineSpacing

    private val shape: SDF = lines
        .flatMapIndexed { lineIndex, line ->
            // Where each character starts along the line: glyphs can have different widths.
            val offsets = line.runningFold(0.0) { x, character ->
                x + advance(character, glyphHeight, thickness, spacing)
            }
            line.mapIndexedNotNull { characterIndex, character ->
                character.takeIf { it in LATIN_ALPHABET }?.toSdf(
                    start = Position(
                        x = start.x + offsets[characterIndex],
                        y = start.y - lineIndex * lineAdvance,
                    ),
                    height = glyphHeight,
                    thickness = thickness,
                )
            }
        }
        .union()

    /** Width of the longest line, including stroke thickness. */
    val width: Double = lines.maxOf { widthOf(it, glyphHeight, thickness, spacing) }

    /** Total height of all lines, including stroke thickness and [lineSpacing]. */
    val height: Double = lines.size * (glyphHeight + 2 * thickness) + (lines.size - 1) * lineSpacing

    /** Number of lines in this block, including empty lines. */
    val lineCount: Int = lines.size

    override fun invoke(position: Position): Double = shape(position)

    /** The letters a [TextBlock] can render. */
    companion object {
        /** The supported Latin letters. */
        val supportedLetters: Set<Char> = LATIN_ALPHABET.toSet()

        /** Creates a text block whose bounding box is centered on [center]. */
        fun centeredAt(
            center: Position,
            text: String,
            glyphHeight: Double,
            thickness: Double = 0.0,
            spacing: Double = glyphHeight / 4,
            lineSpacing: Double = glyphHeight / 4,
        ): TextBlock {
            val normalizedLines = normalizedLines(text)
            val width = normalizedLines.maxOf { widthOf(it, glyphHeight, thickness, spacing) }
            val lineAdvance = glyphHeight + 2 * thickness + lineSpacing
            val start = Position(
                x = center.x - width / 2 + thickness,
                y = center.y - glyphHeight / 2 + (normalizedLines.size - 1) * lineAdvance / 2,
            )
            return TextBlock(text, start, glyphHeight, thickness, spacing, lineSpacing)
        }

        /** Horizontal distance from the origin of [character] to the origin of the next one. */
        private fun advance(character: Char, glyphHeight: Double, thickness: Double, spacing: Double): Double =
            glyphWidth(character) * glyphHeight + 2 * thickness + spacing

        private fun widthOf(line: String, glyphHeight: Double, thickness: Double, spacing: Double): Double =
            if (line.isEmpty()) 0.0 else line.sumOf { advance(it, glyphHeight, thickness, spacing) } - spacing

        private fun normalizedLines(text: String): List<String> = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .uppercase()
            .split('\n')
    }
}
