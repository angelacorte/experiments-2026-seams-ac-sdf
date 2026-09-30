package it.unibo.collektive.alchemist.device

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.collektive.geometry.SpeedControl2D
import it.unibo.collektive.geometry.Vector2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.apache.commons.math3.random.RandomGenerator

/**
 * The body frame of a node, in which it commands its velocity: the frame of the environment rotated by a random
 * angle, unknown to the node (it is where the node happens to point at the start).
 *
 * @property node the node associated with this property
 * @property randomGenerator the generator drawing the rotation of the body frame
 */
class BodyFrameProperty<T : Any>(override val node: Node<T>, private val randomGenerator: RandomGenerator) :
    NodeProperty<T> {

    internal val heading = PI * 2 * randomGenerator.nextDouble()

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = BodyFrameProperty(node, randomGenerator)

    /** Rotates [velocity] from the body frame of the node to the environment. */
    internal fun toEnvironment(velocity: Vector2D) = SpeedControl2D(
        cos(heading) * velocity.x - sin(heading) * velocity.y,
        sin(heading) * velocity.x + cos(heading) * velocity.y,
    )
}
