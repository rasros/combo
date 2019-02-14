package combo.sat.solvers

import combo.math.Vector
import combo.sat.*
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A solver can generate a random [witness] that satisfy the constraints and
 * iterate over the possible solutions with [sequence].
 */
interface Solver : Iterable<Instance> {

    /**
     * Generates a random solution, ie. a witness.
     * @param assumptions these variables will be fixed during solving, see [Literal].
     */
    fun witness(assumptions: Literals = EMPTY_INT_ARRAY): Instance? {
        return try {
            witnessOrThrow(assumptions)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * @param assumptions these variables will be fixed during solving, see [Literal].
     * @throws ValidationException if there is a logical error in the problem or a solution cannot be found with the
     * allotted resources..
     */
    fun witnessOrThrow(assumptions: Literals = EMPTY_INT_ARRAY): Instance

    /**
     * Note that the iterator cannot be used in parallel, but multiple iterators can be used in parallel from the same
     * solver.
     */
    override fun iterator() = sequence().iterator()

    /**
     * Note that the sequence cannot be used in parallel, but multiple sequences can be used in parallel from the same
     * solver. The method does not throw exceptions if
     * @param assumptions these variables will be fixed during solving, see [Literal].
     */
    fun sequence(assumptions: Literals = EMPTY_INT_ARRAY): Sequence<Instance> {
        return generateSequence { witness(assumptions) }
    }

    /**
     * Set the random seed to a specific value to have a reproducible algorithm.
     */
    var randomSeed: Long

    /**
     * The solver will abort after timeout in milliseconds have been reached, without a real-time guarantee.
     */
    var timeout: Long
}

/**
 * An optimizer minimizes an [ObjectiveFunction].
 */
interface Optimizer<in O : ObjectiveFunction> {

    /**
     * Minimize the [function], optionally with the additional constraints in [assumptions].
     * Returns null if no instance can be found.
     * @param function the objective function to optimize on.
     * @param assumptions these variables will be fixed during solving, see [Literal].
     */
    fun optimize(function: O, assumptions: Literals = EMPTY_INT_ARRAY): Instance? {
        return try {
            optimizeOrThrow(function, assumptions)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * Minimize the [function], optionally with the additional constraints in [assumptions].
     * @param function the objective function to optimize on.
     * @param assumptions these variables will be fixed during solving, see [Literal].
     * @throws ValidationException if there is a logical error in the problem or a solution cannot be found with the
     * allotted resources.
     */
    fun optimizeOrThrow(function: O, assumptions: Literals = EMPTY_INT_ARRAY): Instance

    /**
     * Set the random seed to a specific value to have a reproducible algorithm.
     */
    var randomSeed: Long

    /**
     * The solver will abort after timeout in milliseconds have been reached, without a real-time guarantee.
     */
    var timeout: Long
}

interface ObjectiveFunction {
    /**
     * Value to minimize evaluated on a [Instance], which take on values between zeros and ones.
     */
    fun value(instance: Instance): Double

    /**
     * For information about possibilities, see:
     * Penalty Function Methods for Constrained Optimization with Genetic Algorithms
     * http://dx.doi.org/10.3390/mca10010045
     */
    fun penalty(violations: Int) = (violations * violations).toDouble()

    /**
     * Optionally implemented. Optimal bound on function, if reached during search the algorithm will terminate immediately.
     */
    fun lowerBound(): Double = Double.NEGATIVE_INFINITY

    /**
     * Override for efficiency reasons. New value should be previous value - improvement.
     */
    fun improvement(instance: Instance, ix: Ix, propagations: Literals): Double {
        val copy = instance.copy()
        copy.flip(ix)
        copy.setAll(propagations)
        val v1 = value(instance)
        val v2 = value(copy)
        return v1 - v2
    }
}

/**
 * Linear sum objective, as in linear programming.
 */
open class LinearObjective(val maximize: Boolean, val weights: Vector) : ObjectiveFunction {

    private val lowerBound: Double = if (maximize) -weights.sumByDouble { max(0.0, it) } else
        weights.sumByDouble { min(0.0, it) }

    override fun value(instance: Instance) = (instance dot weights).let {
        if (maximize) -it else it
    }

    override fun lowerBound() = lowerBound

    private fun improvementLiteral(instance: Instance, literal: Literal) =
            if (instance.literal(literal.toIx()) == literal) 0.0
            else {
                val w = weights[literal.toIx()].let { if (instance[literal.toIx()]) it else -it }
                if (maximize) -w else w
            }

    override fun improvement(instance: Instance, ix: Literal, propagations: Literals): Double {
        return improvementLiteral(instance, !instance.literal(ix)) + propagations.sumByDouble {
            improvementLiteral(instance, it)
        }
    }
}

/**
 * Used to turn an [Optimizer] into a boolean sat [Solver].
 */
object SatObjective : ObjectiveFunction {
    override fun value(instance: Instance) = 0.0
    override fun lowerBound() = 0.0
    override fun improvement(instance: Instance, ix: Literal, propagations: Literals) = 0.0
    override fun penalty(violations: Int) = violations.toDouble()
}

/**
 * Exterior penalty added to objective used by genetic algorithms.
 */
interface PenaltyFunction {
    fun penalty(value: Double, violations: Int): Double
}

class SquaredPenalty : PenaltyFunction {
    override fun penalty(value: Double, violations: Int) = violations.toDouble().pow(2)
}
