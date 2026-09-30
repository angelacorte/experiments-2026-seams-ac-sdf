package it.unibo.alchemist.boundary.effects

import it.unibo.alchemist.boundary.swingui.effect.api.Effect
import it.unibo.alchemist.boundary.ui.api.Wormhole2D
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position2D
import it.unibo.collektive.alchemist.device.BodyFrameProperty
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal const val LENGTH = 5.0 // Units of the environment
private const val HEAD = 7.0 // Pixels
private const val HEAD_ANGLE = 0.45 // Radians
private const val STROKE = 1.5f

/**
 * Draws where each node with a [BodyFrameProperty] points: an arrow along the x-axis of its body frame, the way a
 * command "forward" moves it. The node itself does not know this direction.
 * It lives under `it.unibo.alchemist`, the only packages where the GUI looks for effects.
 */
@Suppress("DEPRECATION")
class DrawHeading : Effect {
    override fun getColorSummary(): Color = Color.BLACK

    override fun <T : Any, P : Position2D<P>> apply(
        g: Graphics2D,
        node: Node<T>,
        environment: Environment<T, P>,
        wormhole: Wormhole2D<P>,
    ) {
        val heading = node.properties.filterIsInstance<BodyFrameProperty<*>>().firstOrNull()?.heading ?: return
        g.drawArrow(environment, wormhole, node, SpeedControl2D(cos(heading), sin(heading)), LENGTH, Color.BLACK)
    }
}

/** Draws an arrow [length] long from [node] along [direction], in the environment; nothing if either is zero. */
internal fun <T : Any, P : Position2D<P>> Graphics2D.drawArrow(
    environment: Environment<T, P>,
    wormhole: Wormhole2D<P>,
    node: Node<T>,
    direction: Vector2D,
    length: Double,
    color: Color,
) {
    val norm = hypot(direction.x, direction.y)
    if (norm == 0.0 || !norm.isFinite() || length == 0.0) return
    val position = environment.getPosition(node)
    val from = wormhole.getViewPoint(position)
    val to = wormhole.getViewPoint(
        environment.makePosition(position.x + direction.x / norm * length, position.y + direction.y / norm * length),
    )
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble()) // On screen, y grows downwards
    val head = min(HEAD, from.distance(to) / 2) // Short arrows get a small head
    fun corner(side: Double) = Pair(
        (to.x - head * cos(angle + side * HEAD_ANGLE)).toInt(),
        (to.y - head * sin(angle + side * HEAD_ANGLE)).toInt(),
    )
    val (leftX, leftY) = corner(1.0)
    val (rightX, rightY) = corner(-1.0)
    this.color = color
    stroke = BasicStroke(STROKE)
    drawLine(from.x, from.y, to.x, to.y)
    fill(Polygon(intArrayOf(to.x, leftX, rightX), intArrayOf(to.y, leftY, rightY), 3))
}
