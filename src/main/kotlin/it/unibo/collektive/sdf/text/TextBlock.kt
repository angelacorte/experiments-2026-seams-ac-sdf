package it.unibo.collektive.sdf.text

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.expand
import it.unibo.collektive.sdf.scale
import it.unibo.collektive.sdf.translate
import it.unibo.collektive.sdf.union

/** The default spacing between letters and between lines, as a fraction of the glyph height. */
internal const val DEFAULT_SPACING = 0.25

/**
 * A block of SDF text laid out from a start position.
 *
 * Letters are case-insensitive, spaces advance horizontally without drawing a glyph, and newlines start a new
 * line below the previous one. Empty lines are preserved.
 */
class TextBlock private constructor(lines: List<String>, style: TextStyle, start: Position) :
    SDF by render(lines, style, start) {
    /**
     * @param text Text made of the letters in [supportedLetters], spaces, and newlines.
     * @param start Glyph origin of the first character in the first line.
     * @param glyphHeight Height of each letter.
     * @param thickness Thickness applied to the letters' strokes.
     * @param spacing Empty horizontal space between consecutive character cells.
     * @param lineSpacing Empty vertical space between consecutive lines.
     */
    constructor(
        text: String,
        start: Position,
        glyphHeight: Double,
        thickness: Double = 0.0,
        spacing: Double = DEFAULT_SPACING * glyphHeight,
        lineSpacing: Double = DEFAULT_SPACING * glyphHeight,
    ) : this(linesOf(text), TextStyle(glyphHeight, thickness, spacing, lineSpacing), start)

    /** Number of lines in this block, including empty lines. */
    val lineCount: Int = lines.size

    /** Width of the longest line, including stroke thickness. */
    val width: Double = style.widthOf(lines)

    /** Total height of all lines, including stroke thickness and line spacing. */
    val height: Double = lineCount * style.lineAdvance - style.lineSpacing

    /** The letters a [TextBlock] can render. */
    companion object {
        /** The supported Latin letters. */
        val supportedLetters: Set<Char> = Alphabet.glyphs.keys

        /** Creates a text block whose bounding box is centered on [center]. */
        fun centeredAt(
            center: Position,
            text: String,
            glyphHeight: Double,
            thickness: Double = 0.0,
            spacing: Double = DEFAULT_SPACING * glyphHeight,
            lineSpacing: Double = DEFAULT_SPACING * glyphHeight,
        ): TextBlock {
            val lines = linesOf(text)
            val style = TextStyle(glyphHeight, thickness, spacing, lineSpacing)
            val start = Position(
                x = center.x - style.widthOf(lines) / 2 + thickness,
                y = center.y - glyphHeight / 2 + (lines.size - 1) * style.lineAdvance / 2,
            )
            return TextBlock(lines, style, start)
        }

        /** Splits [text] into uppercase lines, checking that it can be rendered. */
        private fun linesOf(text: String): List<String> = text.uppercase().lines().also { lines ->
            val characters = lines.joinToString("").toSet()
            require(characters.any { it in supportedLetters }) { "A text block needs at least one letter" }
            val unsupported = characters - supportedLetters - ' '
            require(unsupported.isEmpty()) {
                "No SDF glyph for $unsupported: supported letters are $supportedLetters, spaces, and newlines"
            }
        }

        /**
         * Lays the glyphs out in normalized coordinates (the glyph height is 1), thickens them all at once, and
         * only then scales the whole block to the glyph height and moves it to [start].
         */
        private fun render(lines: List<String>, style: TextStyle, start: Position): SDF {
            val unit = style.glyphHeight
            return lines
                .flatMapIndexed { lineIndex, line ->
                    // Where each character starts along the line: glyphs can have different widths.
                    val offsets = line.runningFold(0.0) { x, character -> x + style.advance(character) }
                    line.mapIndexedNotNull { characterIndex, character ->
                        Alphabet.glyphs[character]?.shape?.translate(
                            dx = offsets[characterIndex] / unit,
                            dy = -lineIndex * style.lineAdvance / unit,
                        )
                    }
                }
                .union()
                .expand(style.thickness / unit)
                .scale(unit)
                .translate(start.x, start.y)
        }
    }
}

/** The metrics of a [TextBlock], in world units. */
internal data class TextStyle(
    val glyphHeight: Double,
    val thickness: Double = 0.0,
    val spacing: Double = DEFAULT_SPACING * glyphHeight,
    val lineSpacing: Double = DEFAULT_SPACING * glyphHeight,
) {
    init {
        require(glyphHeight > 0.0) { "Glyph height must be positive, got $glyphHeight" }
        require(thickness >= 0.0) { "Glyph thickness cannot be negative, got $thickness" }
        require(spacing >= 0.0) { "Character spacing cannot be negative, got $spacing" }
        require(lineSpacing >= 0.0) { "Line spacing cannot be negative, got $lineSpacing" }
    }

    /** Vertical distance from the origin of a line to the origin of the next one. */
    val lineAdvance: Double = glyphHeight + 2 * thickness + lineSpacing

    /** Horizontal distance from the origin of [character] to the origin of the next one. */
    fun advance(character: Char): Double =
        (Alphabet.glyphs[character]?.width ?: GLYPH_WIDTH) * glyphHeight + 2 * thickness + spacing

    /** Width of [line], including stroke thickness. */
    fun widthOf(line: String): Double = if (line.isEmpty()) 0.0 else line.sumOf(::advance) - spacing

    /** Width of the longest of [lines]. */
    fun widthOf(lines: List<String>): Double = lines.maxOf(::widthOf)
}
