package combo.sat.solvers

import combo.math.Vector
import combo.sat.*
import combo.util.EMPTY_INT_ARRAY
import combo.util.sumByFloat
import kotlin.math.max
import kotlin.math.min

/**
 * A solver can generate a random [witness] that satisfy the constraints and
 * iterate over the possible solutions with [asSequence].
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
    override fun iterator() = asSequence().iterator()

    /**
     * Note that the sequence cannot be used in parallel, but multiple sequences can be used in parallel from the same
     * solver. The method does not throw exceptions if
     * @param assumptions these variables will be fixed during solving, see [Literal].
     */
    fun asSequence(assumptions: Literals = EMPTY_INT_ARRAY): Sequence<Instance> {
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
    fun value(instance: Instance): Float

    /**
     * Optionally implemented. Optimal bound on function, if reached during search the algorithm will terminate immediately.
     */
    fun lowerBound(): Float = Float.NEGATIVE_INFINITY

    fun upperBound(): Float = Float.POSITIVE_INFINITY

    /**
     * Override for efficiency reasons. New value should be previous value - improvement.
     */
    fun improvement(instance: MutableInstance, ix: Int): Float {
        val v1 = value(instance)
        instance.flip(ix)
        val v2 = value(instance)
        instance.flip(ix)
        return v1 - v2
    }
}

/**
 * Linear sum objective, as in linear programming.
 */
open class LinearObjective(val maximize: Boolean, val weights: Vector) : ObjectiveFunction {

    private val lowerBound: Float = if (maximize) -weights.sumByFloat { max(0.0f, it) } else
        weights.sumByFloat { min(0.0f, it) }
    private val upperBound: Float = if (maximize) -weights.sumByFloat { min(0.0f, it) } else
        weights.sumByFloat { max(0.0f, it) }

    override fun value(instance: Instance) = (instance dot weights).let {
        if (maximize) -it else it
    }

    override fun lowerBound() = lowerBound
    override fun upperBound() = upperBound

    override fun improvement(instance: MutableInstance, ix: Literal): Float {
        val literal = !instance.literal(ix)
        return if (literal in instance) 0.0f
        else {
            val w = weights[literal.toIx()].let { if (instance[literal.toIx()]) it else -it }
            if (maximize) -w else w
        }
    }
}

/**
 * Used to turn an [Optimizer] into a boolean sat [Solver].
 */
object SatObjective : ObjectiveFunction {
    override fun value(instance: Instance) = 0.0f
    override fun lowerBound() = 0.0f
    override fun upperBound() = 0.0f
    override fun improvement(instance: MutableInstance, ix: Literal) = 0.0f
}

/**
 * Exterior penalty added to objective used by genetic algorithms.
 * For information about possibilities, see:
 * Penalty Function Methods for Constrained Optimization with Genetic Algorithms
 * http://dx.doi.org/10.3390/mca10010045
 */
interface PenaltyFunction {
    fun penalty(value: Float, violations: Int, lowerBound: Float, upperBound: Float): Float
}

class LinearPenalty : PenaltyFunction {
    override fun penalty(value: Float, violations: Int, lowerBound: Float, upperBound: Float): Float = violations.toFloat()
}

class SquaredPenalty : PenaltyFunction {
    override fun penalty(value: Float, violations: Int, lowerBound: Float, upperBound: Float) = violations.let { (it * it).toFloat() }
}

class DisjunctPenalty(private val extended: PenaltyFunction = LinearPenalty()) : PenaltyFunction {
    override fun penalty(value: Float, violations: Int, lowerBound: Float, upperBound: Float): Float {
        if (violations > 0) return upperBound - lowerBound + extended.penalty(value, violations, lowerBound, upperBound)
        else return 0.0f
    }
}
