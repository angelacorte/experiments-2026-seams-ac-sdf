package it.unibo.collektive.alchemist.device.sensors

import it.unibo.collektive.entrypoint.ShapeCatalog

interface ShapeDefinition {

    fun shape(name: String): ShapeCatalog = ShapeCatalog.valueOf(name.uppercase())
}
