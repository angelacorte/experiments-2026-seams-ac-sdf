package it.unibo.collektive.catalog

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import org.yaml.snakeyaml.Yaml

/**
 * The shapes defined in `shapes.yml` (in the resources), by name: the simulation files batch over these names.
 * Each entry has a `type`, an [SDF] class (its simple name, if in `it.unibo.collektive.sdf.shape`, `.primitive` or
 * `.text`), and the parameters of one of its public constructors, by name: positions as `[x, y]`, angles in radians,
 * optional parameters may be left out.
 * The positions are where the shape lies with global positions; the other scenarios move its [TargetShape.center]
 * where the shape belongs (the leader, the anchors' centroid).
 */
object ShapeCatalog {
    private val shapePackages = listOf("shape", "primitive", "text").map { "it.unibo.collektive.sdf.$it" }

    private val definitions: Map<String, Map<String, Any>> by lazy {
        val stream = requireNotNull(ShapeCatalog::class.java.getResourceAsStream("/shapes.yml")) {
            "shapes.yml not found in the classpath"
        }
        stream.use { Yaml().load(it) }
    }

    /** The names of the shapes. */
    val names: Set<String> get() = definitions.keys

    /** The shape called [name], where `shapes.yml` puts it. */
    fun named(name: String): TargetShape {
        val definition = requireNotNull(definitions[name]) { "Unknown shape '$name', expected one of $names" }
        val type = classOf(definition.getValue("type").toString())
        val arguments = definition - "type"
        val constructor = type.constructors
            .filter { it.visibility == KVisibility.PUBLIC }
            .firstOrNull { it.accepts(arguments.keys) }
            ?: error(
                "Shape '$name': no constructor of ${type.simpleName} takes ${arguments.keys}, expected one of " +
                    type.constructors.map { constructor -> constructor.parameters.map { it.name } },
            )
        val values = constructor.parameters
            .filter { it.name in arguments }
            .associateWith { convert(arguments.getValue(checkNotNull(it.name)), it.type) }
        val sdf = constructor.callBy(values) as SDF
        val positions = values.mapKeys { (parameter, _) -> parameter.name }
        return TargetShape(sdf, centerOf(positions))
    }

    /**
     * The `center` (or `origin`, or `start`) of the shape; for shapes made of points (e.g., the vertices of a
     * Triangle or of a Polygon), their centroid.
     */
    private fun centerOf(parameters: Map<String?, Any>): Position {
        val reference = listOf("center", "origin", "start").firstNotNullOfOrNull { parameters[it] as? Position }
        val points = parameters.values.flatMap { value ->
            (value as? List<*>)?.filterIsInstance<Position>() ?: listOfNotNull(value as? Position)
        }
        return reference ?: points.takeIf { it.isNotEmpty() }
            ?.let { Position(it.sumOf(Position::x) / it.size, it.sumOf(Position::y) / it.size) }
            ?: Position.origin
    }

    private fun classOf(type: String): KClass<*> = (listOf(type) + shapePackages.map { "$it.$type" })
        .firstNotNullOfOrNull { runCatching { Class.forName(it).kotlin }.getOrNull() }
        ?.takeIf { SDF::class.java.isAssignableFrom(it.java) }
        ?: error("Unknown SDF type '$type'")

    /** Whether this constructor takes exactly the parameters [keys], plus optional ones. */
    private fun KFunction<*>.accepts(keys: Set<String>): Boolean {
        val names = parameters.mapNotNull { it.name }.toSet()
        return keys.all { it in names } && parameters.all { it.isOptional || it.name in keys }
    }

    private fun convert(value: Any, type: KType): Any = when (type.classifier) {
        Double::class -> (value as Number).toDouble()
        Int::class -> (value as Number).toInt()
        String::class -> value.toString()
        Position::class -> positionOf(value)
        List::class -> (value as List<*>).map { positionOf(checkNotNull(it)) }
        else -> value
    }

    private fun positionOf(value: Any): Position {
        val (x, y) = (value as List<*>).map { (it as Number).toDouble() }
        return Position(x, y)
    }
}

/**
 * A shape of the [ShapeCatalog]: its [sdf], where `shapes.yml` puts it, and its [center] (its `center`, `origin` or
 * `start`), the point the scenarios without global positions move where the shape belongs.
 */
data class TargetShape(val sdf: SDF, val center: Position)
