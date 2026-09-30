package it.unibo.collektive.formation

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.alchemist.device.parameter
import it.unibo.collektive.alchemist.device.textParameter

/**
 * The parameters of the lattice formation.
 *
 * @property ringSize the nearest neighbors forming the first ring of the lattice.
 * @property repulsion how the neighbors push a device away.
 * @property outsideGain the scale of the repulsion outside the shape, where the SDF pulls the devices in.
 * @property gradientStep the step of the finite differences on the SDF.
 * @property maxSpeed the maximum speed of a device.
 * @property spacing how the lattice spacing evolves.
 * @property step how the step size adapts.
 */
data class LatticeParameters(
    val ringSize: Int,
    val repulsion: RepulsionLaw,
    val outsideGain: Double,
    val gradientStep: Double,
    val maxSpeed: Double,
    val spacing: SpacingRule,
    val step: StepRule,
)

/**
 * Reads the [LatticeParameters] from the molecules of the simulation file (see `repulsionOnly.yml`): the `repulsion`
 * (`softDisk` or `inverseSquare`) and the `spacing` (`adaptive` or `fixed`) pick the modes, each reading only its own
 * parameters.
 */
fun CollektiveDevice<*>.latticeParameters(): LatticeParameters = LatticeParameters(
    ringSize = parameter("ringSize").toInt(),
    repulsion = when (val mode = textParameter("repulsion")) {
        "softDisk" -> RepulsionLaw.SoftDisk(stiffness = parameter("softDiskStiffness"))
        "inverseSquare" -> RepulsionLaw.InverseSquare(coefficient = parameter("inverseSquareCoefficient"))
        else -> error("Unknown repulsion '$mode': use softDisk or inverseSquare")
    },
    outsideGain = parameter("outsideRepulsionGain"),
    gradientStep = parameter("sdfGradientStep"),
    maxSpeed = parameter("maxSpeed"),
    spacing = when (val mode = textParameter("spacing")) {
        "fixed" -> SpacingRule.Fixed(spacing = parameter("fixedSpacing"))
        "adaptive" -> SpacingRule.Adaptive(
            initial = parameter("initialSpacing"),
            rate = parameter("spacingRate"),
            pressureMargin = parameter("pressureMargin"),
            max = parameter("maxSpacingFraction") * parameter("communicationRange"),
        )
        else -> error("Unknown spacing '$mode': use adaptive or fixed")
    },
    step = StepRule(
        decrease = parameter("stepDecrease"),
        increase = parameter("stepIncrease"),
        minGain = parameter("minStepGain"),
    ),
)
