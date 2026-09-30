package it.unibo.alchemist.boundary.effects

import it.unibo.alchemist.boundary.swingui.effect.api.Effect
import it.unibo.alchemist.boundary.ui.api.Wormhole2D
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position2D
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.alchemist.device.BodyFrameProperty
import it.unibo.collektive.geometry.Vector2D
import java.awt.Color
import java.awt.Graphics2D
import kotlin.math.min

/**
 * Draws, for each node that is not an anchor, two directions in the environment: in a soft color the ideal command
 * (the control the node computes in the anchor frame, placed with the true positions of the anchors), and the velocity
 * that the motors actually apply. They meet as the node learns how its commands move it among the anchors.
 * Their length is proportional to the speed, and [LENGTH] at `maxSpeed`, to which the ideal command is also capped.
 */
@Suppress("DEPRECATION")
class DrawActuation : Effect {
    override fun getColorSummary(): Color = APPLIED

    override fun <T : Any, P : Position2D<P>> apply(
        g: Graphics2D,
        node: Node<T>,
        environment: Environment<T, P>,
        wormhole: Wormhole2D<P>,
    ) {
        if (node.getConcentration(LEADER) == true) return // The anchors stay still
        val frame = TrueAnchorFrame.of(environment)
        val ideal = (node.getConcentration(CONTROL) as? Vector2D)?.let { frame?.toEnvironment(it) }
        val body = node.properties.filterIsInstance<BodyFrameProperty<*>>().firstOrNull()
        val applied = (node.getConcentration(VELOCITY) as? Vector2D)?.let { body?.toEnvironment(it) ?: it }
        val maxSpeed = (node.getConcentration(MAX_SPEED) as? Number)?.toDouble() ?: 1.0
        fun Vector2D.length() = LENGTH * min(norm, maxSpeed) / maxSpeed
        ideal?.let { g.drawArrow(environment, wormhole, node, it, it.length(), IDEAL) }
        applied?.let { g.drawArrow(environment, wormhole, node, it, it.length(), APPLIED) }
    }

    private companion object {
        val LEADER = SimpleMolecule("leader")
        val CONTROL = SimpleMolecule("control")
        val VELOCITY = SimpleMolecule("Velocity")
        val MAX_SPEED = SimpleMolecule("maxSpeed")
        val IDEAL = Color(120, 170, 255, 190)
        val APPLIED = Color(220, 80, 30)
    }
}
