package it.unibo.collektive.library

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.deepestPointFrom
import it.unibo.collektive.sdf.translate
import it.unibo.collektive.stdlib.spreading.hopGradientCast

/**
 * The shape held by a device: its [name] in the catalog, and the point [inside] it (where `shapes.yml` puts it) that
 * the devices place where the shape belongs (the leader, or the centroid of the anchors).
 */
private data class HeldShape(val name: String, val inside: Position)

/**
 * The shape held by the [leader], spread to every device by its nearest leader, moved so that the point inside it
 * that the leader found lies on [point] (the leader, or the centroid of the anchors, in the frame of the device).
 * The leader takes its shape where `shapes.yml` puts it, and finds the point deep inside it nearest to its own [point]
 * (see [deepestPointFrom]): down to the border, then inward along the gradient of the SDF.
 * The shape is the `shape` molecule of the leader (the name of the [formation] when missing), so that changing the
 * molecule on the leader reshapes the swarm.
 * Every device stores the shape it perceives in its `shape` molecule, and the point inside it in `shapeCenter`, at
 * every round: a newly elected leader keeps the current shape, and the metrics measure the shape the devices actually
 * form.
 */
fun Aggregate<Int>.leaderShape(
    device: CollektiveDevice<*>,
    leader: Boolean,
    formation: ShapeProperty<*>,
    point: Position,
): SDF {
    val name = device.getOrNull<String>("shape") ?: formation.name
    // Only the leader looks for the point inside, the others take the one it spreads.
    val local = HeldShape(name, if (leader) formation.target(name).sdf.deepestPointFrom(point) else point)
    val (held, inside) = hopGradientCast(leader, local)
    device["shape"] = held
    device["shapeCenter"] = inside
    return formation.target(held).sdf.translate(point.x - inside.x, point.y - inside.y)
}
