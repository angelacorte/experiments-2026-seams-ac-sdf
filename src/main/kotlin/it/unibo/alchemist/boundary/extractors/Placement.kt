package it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.boundary.effects.TrueAnchorFrame
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.catalog.TargetShape
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.translate

private val LEADER = SimpleMolecule("leader")
private val SHAPE = SimpleMolecule("shape")
private val X_AXIS = SpeedControl2D(1.0, 0.0)
private val Y_AXIS = SpeedControl2D(0.0, 1.0)

/** Fails unless [placement] is one of those of [FormationMetrics]: `global`, `leader` or `anchors`. */
internal fun requireKnownPlacement(placement: String) = require(placement in setOf("global", "leader", "anchors")) {
    "Unknown placement $placement: global, leader or anchors"
}

/**
 * The shape of the devices in [environment] and where it lies, by [placement] (see [FormationMetrics]), or null until
 * the leader or the anchors are elected.
 * The shape is the one in the `shape` molecule of the leader (the one the devices perceive, which may change during
 * the simulation), or the one of its [ShapeProperty] when missing.
 */
internal fun <T> shapeIn(environment: Environment<T, *>, placement: String): Pair<TargetShape, Placement>? {
    // ponytail: with more leaders (a split network), the one with the highest id
    val holder = environment.nodes.filter { it.getConcentration(LEADER) == true }.maxByOrNull { it.id }
        ?: environment.nodes.firstOrNull()
    val property = environment.nodes
        .firstNotNullOfOrNull { node -> node.properties.filterIsInstance<ShapeProperty<*>>().firstOrNull() }
    return property?.let {
        val shape = it.target(holder?.getConcentration(SHAPE) as? String ?: it.name)
        placementOf(environment, placement, shape)?.let { where -> shape to where }
    }
}

private fun <T> placementOf(environment: Environment<T, *>, placement: String, shape: TargetShape): Placement? =
    when (placement) {
        "global" -> Placement(Position.origin, X_AXIS, Y_AXIS, shape.center, shape)
        // ponytail: with more leaders (a split network), the one with the highest id
        "leader" -> environment.nodes.filter { it.getConcentration(LEADER) == true }.maxByOrNull { it.id }?.let {
            val origin = environment.getPosition(it).coordinates
            Placement(Position(origin[0], origin[1]), X_AXIS, Y_AXIS, Position.origin, shape)
        }
        else -> {
            @Suppress("UNCHECKED_CAST") // The scenarios are all in the Euclidean plane
            TrueAnchorFrame.of(environment as Environment<T, Euclidean2DPosition>)?.let {
                Placement(Position(it.origin[0], it.origin[1]), it.xAxis, it.yAxis, it.centroid, shape)
            }
        }
    }

/**
 * Where the shape lies in the environment: the center of the shape of `shapes.yml` on [point] of the frame with
 * [origin] and axes [xAxis] and [yAxis] (orthonormal, possibly mirrored).
 */
internal class Placement(
    private val origin: Position,
    private val xAxis: Vector2D,
    private val yAxis: Vector2D,
    private val point: Position,
    private val shape: TargetShape,
) {
    private val inFrame = shape.sdf.translate(point.x - shape.center.x, point.y - shape.center.y)

    /** The shape, in the environment: rigid moves keep its distances exact. */
    val sdf = SDF { position ->
        val dx = position.x - origin.x
        val dy = position.y - origin.y
        inFrame(Position(dx * xAxis.x + dy * xAxis.y, dx * yAxis.x + dy * yAxis.y))
    }

    /** The environment position of [position] of the shape of `shapes.yml`. */
    fun toEnvironment(position: Position): Position {
        val x = position.x - shape.center.x + point.x
        val y = position.y - shape.center.y + point.y
        return Position(origin.x + x * xAxis.x + y * yAxis.x, origin.y + x * xAxis.y + y * yAxis.y)
    }
}
