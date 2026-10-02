package it.unibo.collektive.entrypoint

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.ring
import it.unibo.collektive.sdf.shape.CircularSector
import it.unibo.collektive.sdf.shape.Crescent
import it.unibo.collektive.sdf.shape.CutDisk
import it.unibo.collektive.sdf.shape.FibonacciSpiral
import it.unibo.collektive.sdf.shape.Hexagon
import it.unibo.collektive.sdf.shape.Horseshoe
import it.unibo.collektive.sdf.shape.QuestionMark
import it.unibo.collektive.sdf.shape.RegularPolygon
import it.unibo.collektive.sdf.shape.RoundedX
import it.unibo.collektive.sdf.shape.Spiral
import it.unibo.collektive.sdf.shape.Square
import it.unibo.collektive.sdf.shape.Stairs
import it.unibo.collektive.sdf.shape.Star
import it.unibo.collektive.sdf.shape.Triangle
import it.unibo.collektive.sdf.shape.Vesica
import it.unibo.collektive.sdf.text.toSdf
import it.unibo.collektive.sdf.translate
import org.yaml.snakeyaml.Yaml

/**
 * The shapes defined in `shapes.yml` (in the resources), by name: the simulation files batch over these names.
 * Each entry has a `kind` (a [ShapeKind], in lowercase) and the parameters of that kind.
 */
object ShapeCatalog {
    private val definitions: Map<String, ShapeParameters> by lazy {
        val stream = requireNotNull(ShapeCatalog::class.java.getResourceAsStream("/shapes.yml")) {
            "shapes.yml not found in the classpath"
        }
        stream.use { Yaml().load<Map<String, Map<String, Any>>>(it) }
            .mapValues { (name, entry) -> ShapeParameters(name, entry) }
    }

    /** The names of the shapes. */
    val names: Set<String> get() = definitions.keys

    /** The shape called [name], in its local frame. */
    fun named(name: String): SDF {
        val parameters = requireNotNull(definitions[name]) { "Unknown shape '$name', expected one of $names" }
        return ShapeKind.valueOf(parameters.textIn("kind").uppercase()).build(parameters)
    }
}

/** The shape moved so that the origin of its frame lies on [origin]. */
fun SDF.placedAt(origin: Position): SDF = translate(origin.x, origin.y)

/**
 * The kinds of shape, each built from its parameters (angles in degrees).
 * Every shape contains the origin of its local frame: the scenario moves that origin where the shape belongs
 * (a fixed point, the leader, the anchors' centroid), which thus lies inside the shape.
 */
enum class ShapeKind(val build: (ShapeParameters) -> SDF) {
    /** A star centered on the origin: `radius`, `points`, `spikiness` (optional, `points / 2` by default). */
    STAR({
        val points = it["points"].toInt()
        Star(Position.origin, it["radius"], points, it["spikiness", points / 2.0])
    }),

    /** A regular hexagon centered on the origin: `radius`. */
    HEXAGON({ Hexagon(Position.origin, it["radius"]) }),

    /** A regular polygon centered on the origin: `radius`, `sides`, `rotation` of the first vertex (optional). */
    POLYGON({
        RegularPolygon(
            Position.origin,
            it["radius"],
            it["sides"].toInt(),
            Math.toRadians(it["rotation", 0.0]),
        )
    }),

    /** A square centered on the origin: `side`. */
    SQUARE({ Square(Position.origin, it["side"]) }),

    /** A triangle pointing upwards, with its centroid on the origin: `halfBase`, `height`. */
    TRIANGLE({
        val halfBase = it["halfBase"]
        val height = it["height"]
        Triangle(
            Position(-halfBase, -height / THIRDS),
            Position(halfBase, -height / THIRDS),
            Position(0.0, height * (THIRDS - 1) / THIRDS),
        )
    }),

    /** A circular band, with the origin on the band: `radius`, `halfWidth`. */
    RING({ Circle(Position(it["radius"], 0.0), it["radius"]) ring it["halfWidth"] }),

    /** A crescent, with the origin in the middle of its thick part: `radius`, `offset`. */
    CRESCENT({
        val radius = it["radius"]
        val offset = it["offset"]
        Crescent(Position(radius - offset / 2, 0.0), radius, offset)
    }),

    /** A horseshoe opening upwards, with the origin at the bottom of the band. */
    HORSESHOE({
        val radius = it["radius"]
        Horseshoe(
            Position(0.0, radius),
            radius,
            Math.toRadians(it["aperture"]),
            it["armLength"],
            it["thickness"],
        )
    }),

    /** A circular sector opening upwards, with the origin halfway along its axis: `radius`, `halfAperture`. */
    CIRCULAR_SECTOR({
        val radius = it["radius"]
        CircularSector(Position(0.0, -radius / 2), radius, Math.toRadians(it["halfAperture"]))
    }),

    /** A disk centered on the origin, cut by a horizontal line: `radius`, `cutHeight` (below the origin if < 0). */
    CUT_DISK({ CutDisk(Position.origin, it["radius"], it["cutHeight"]) }),

    /** A vertical lens centered on the origin: `radius`, `offset`. */
    VESICA({ Vesica(Position.origin, it["radius"], it["offset"]) }),

    /** An X with rounded ends, crossing on the origin: `width`, `armHalfWidth`. */
    ROUNDED_X({ RoundedX(Position.origin, it["width"], it["armHalfWidth"]) }),

    /** A staircase going up towards +x, with the origin in the middle of its middle step. */
    STAIRS({
        val width = it["stepWidth"]
        val height = it["stepHeight"]
        val steps = it["steps"].toInt()
        val middle = steps / 2 // The column of the middle step, as tall as middle + 1 steps
        Stairs(Position(-(middle + 0.5) * width, -(middle + 1) * height / 2), width, height, steps)
    }),

    /** A question mark, with the origin in the middle of its vertical stroke: `radius`, `thickness`. */
    QUESTION_MARK({
        val radius = it["radius"]
        QuestionMark(Position(0.0, radius * QUESTION_MARK_STROKE_MIDDLE), radius, it["thickness"])
    }),

    /** A spiral starting from the origin: `spacing`, `turns`, `thickness`, `innerRadius` (optional). */
    SPIRAL({
        Spiral(
            Position.origin,
            it["spacing"],
            it["turns"].toInt(),
            it["innerRadius", 0.0],
            it["thickness"],
        )
    }),

    /** A Fibonacci spiral, whose innermost arc is centered on the origin: `scale` (below `thickness`), ... */
    FIBONACCI_SPIRAL({
        FibonacciSpiral(Position.origin, it["scale"], it["quarterTurns"].toInt(), it["thickness"])
    }),

    /** A `text`, with the origin in the middle of its first letter: `height`, `thickness`. */
    TEXT({
        val height = it["height"]
        val start = Position(-height / GLYPH_MIDDLE_FRACTION, -height / 2)
        it.textIn("text").toSdf(start, height, it["thickness"])
    }),
}

/** The parameters of the shape called [name], as written in `shapes.yml`. */
class ShapeParameters(private val name: String, private val values: Map<String, Any>) {
    /** The numeric parameter [key], or [default] if missing: `parameters["radius"]`, `parameters["rotation", 0.0]`. */
    operator fun get(key: String, default: Double? = null): Double = (values[key] as? Number)?.toDouble()
        ?: requireNotNull(default) { "Shape '$name': missing numeric parameter '$key'" }

    /** The textual parameter [key]. */
    fun textIn(key: String): String = requireNotNull(values[key]) { "Shape '$name': missing parameter '$key'" }.toString()
}

private const val THIRDS = 3.0

/** The segment of the question mark goes from 1 to 2 radii below the center of its arc. */
private const val QUESTION_MARK_STROKE_MIDDLE = 1.5

/** A glyph is half as wide as it is tall: its middle is a quarter of the height from its start. */
private const val GLYPH_MIDDLE_FRACTION = 4.0
