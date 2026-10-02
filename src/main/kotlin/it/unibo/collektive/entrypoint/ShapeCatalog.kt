package it.unibo.collektive.entrypoint

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.translate
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
 * The shapes must contain the origin of their frame, which each scenario moves where the shape belongs.
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

    /** The shape called [name], in its local frame. */
    fun named(name: String): SDF {
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
        return constructor.callBy(values) as SDF
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

/** The shape moved so that the origin of its frame lies on [origin]. */
fun SDF.placedAt(origin: Position): SDF = translate(origin.x, origin.y)
