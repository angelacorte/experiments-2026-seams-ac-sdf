package it.unibo.collektive.alchemist.device.sensors

import it.unibo.collektive.catalog.ShapeCatalog
import it.unibo.collektive.sdf.SDF

/** Something that resolves the shapes of the [ShapeCatalog] by the names used in the simulation files. */
interface ShapeDefinition {
    /** The shape of the [ShapeCatalog] called [name] (e.g., `star` or `cut_disk`), in its local frame. */
    fun shape(name: String): SDF = ShapeCatalog.named(name)
}
