package it.unibo.alchemist.model.reactions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Condition
import it.unibo.alchemist.model.Dependency
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.GlobalReaction
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.timedistributions.Trigger
import it.unibo.alchemist.model.times.DoubleTime
import org.danilopianini.util.ImmutableListSet
import org.danilopianini.util.ListSet

/**
 * A [GlobalReaction] that fires exactly once, at the given simulated [time] (it relies on a [Trigger]).
 * Subclasses provide the effect on the environment by implementing [perform].
 *
 * @param T the type of the concentrations
 * @property environment the environment the reaction acts upon
 */
abstract class TriggeredGlobalReaction<T>(protected val environment: Environment<T, *>, time: Double) :
    GlobalReaction<T> {
    override val timeDistribution: TimeDistribution<T> = Trigger(DoubleTime(time))

    override var actions: List<Action<T>> = emptyList()

    override var conditions: List<Condition<T>> = emptyList()

    // Adding or removing nodes may affect any other reaction.
    override val outboundDependencies: ListSet<out Dependency> = ImmutableListSet.of(Dependency.EVERYTHING)

    override val inboundDependencies: ListSet<out Dependency> = ImmutableListSet.of()

    override fun canExecute(): Boolean = conditions.all { it.isValid }

    override fun compareTo(other: Actionable<T>): Int = tau.compareTo(other.tau)

    override fun update(currentTime: Time, hasBeenExecuted: Boolean, environment: Environment<T, *>) = Unit

    override fun initializationComplete(atTime: Time, environment: Environment<T, *>) = Unit

    override fun execute() {
        perform()
        // After the execution the Trigger moves its next occurrence to +infinity.
        timeDistribution.update(tau, true, 1.0, environment)
    }

    /**
     * The effect of this reaction, executed once at the trigger time.
     */
    protected abstract fun perform()
}
