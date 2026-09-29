package it.unibo.collektive.alchemist.device

import it.unibo.alchemist.collektive.device.CollektiveDevice

/** Reads the numeric parameter [name], set as a molecule in the simulation file. */
fun CollektiveDevice<*>.parameter(name: String): Double =
    requireNotNull(getOrNull<Number>(name)) { "Missing parameter '$name' in the simulation file" }.toDouble()

/** Reads the textual parameter [name] (e.g., the name of a mode), set as a molecule in the simulation file. */
fun CollektiveDevice<*>.textParameter(name: String): String =
    requireNotNull(getOrNull<Any>(name)) { "Missing parameter '$name' in the simulation file" }.toString()
