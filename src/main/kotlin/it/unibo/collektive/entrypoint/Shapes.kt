package it.unibo.collektive.entrypoint

import it.unibo.collektive.geometry.Position
import it.unibo.collektive.sdf.SDF
import it.unibo.collektive.sdf.primitive.Circle
import it.unibo.collektive.sdf.ring
import it.unibo.collektive.sdf.shape.Crescent
import it.unibo.collektive.sdf.shape.Hexagon
import it.unibo.collektive.sdf.shape.Horseshoe
import it.unibo.collektive.sdf.shape.Star
import it.unibo.collektive.sdf.translate
import kotlin.math.PI

/**
 * The shapes the simulations batch over, shared by all the scenarios.
 * Every shape contains the origin of its local frame: the scenario moves that origin where the shape belongs
 * (a fixed point, the leader, the anchors' centroid), which thus lies inside the shape.
 */
enum class ShapeCatalog(val sdf: SDF) {
    STAR(Star(Position.origin, 30.0, 5)),
    HEXAGON(Hexagon(Position.origin, 30.0)),
    RING(Circle(Position(25.0, 0.0), 25.0) ring 6.0), // The origin is on the band
    CRESCENT(Crescent(Position(20.0, 0.0), 30.0, 20.0)), // The origin is in the thick part
    HORSESHOE(Horseshoe(Position(0.0, 20.0), 20.0, PI / 4, 15.0, 6.0)), // The origin is at the bottom of the band
}

/** The shape moved so that the origin of its frame lies on [origin]. */
fun ShapeCatalog.placedAt(origin: Position): SDF = sdf.translate(origin.x, origin.y)
