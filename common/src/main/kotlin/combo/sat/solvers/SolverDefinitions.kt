@file:Suppress("NOTHING_TO_INLINE")

package combo.sat.solvers

import combo.math.RandomSequence
import combo.math.Vector
import combo.sat.*
import combo.util.EMPTY_INT_ARRAY
import combo.util.nanos

/**
 * A solver can generate a random [witness] that satisfy the constraints and
 * iterate over the possible solutions with [sequence].
 */
interface Solver : Iterable<Labeling> {

    fun witness(assumptions: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            witnessOrThrow(assumptions)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * @throws ValidationException
     */
    fun witnessOrThrow(assumptions: Literals = EMPTY_INT_ARRAY): Labeling

    override fun iterator() = sequence().iterator()

    fun sequence(assumptions: Literals = EMPTY_INT_ARRAY): Sequence<Labeling> {
        return generateSequence { witness(assumptions) }
    }

}

/**
 * An optimizer minimizes an [ObjectiveFunction].
 */
interface Optimizer<in O : ObjectiveFunction> {

    /**
     * Minimize the [function], optionally with the additional constraints in [assumptions].
     * Returns null if no labeling can be found.
     */
    fun optimize(function: O, assumptions: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            optimizeOrThrow(function, assumptions)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * Minimize the [function], optionally with the additional constraints in [assumptions].
     * Throws a sub-class of [ValidationException] if no labeling can be found.
     */
    fun optimizeOrThrow(function: O, assumptions: Literals = EMPTY_INT_ARRAY): Labeling
}

interface ObjectiveFunction {
    /**
     * Value to minimize evaluated on a [Labeling], which take on values between zeros and ones.
     */
    fun value(labeling: Labeling): Double

    /**
     * For information about possibilities, see:
     * Penalty Function Methods for Constrained Optimization with Genetic Algorithms
     * https://doi.org/10.3390/mca10010045
     */
    fun penalty(violations: Int) = (violations * violations).toDouble()

    /**
     * Optionally implemented. Optimal bound on function, if reached during search the algorithm will terminate immediately.
     */
    fun lowerBound(): Double = Double.NEGATIVE_INFINITY

    /**
     * Override for efficiency reasons. New value should be previous value - improvement.
     */
    fun improvement(labeling: Labeling, ix: Ix, propagations: Literals): Double {
        val copy = labeling.copy()
        copy.flip(ix)
        copy.setAll(propagations)
        val v1 = value(labeling)
        val v2 = value(copy)
        return v1 - v2
    }
}

/**
 * Linear sum objective, as in linear programming.
 */
open class LinearObjective(val maximize: Boolean, val weights: Vector) : ObjectiveFunction {

    override fun value(labeling: Labeling) = (labeling dot weights).let {
        if (maximize) -it else it
    }

    private inline fun improvementLiteral(labeling: Labeling, literal: Literal) =
            if (labeling.literal(literal.toIx()) == literal) 0.0
            else {
                val w = weights[literal.toIx()].let { if (labeling[literal.toIx()]) it else -it }
                if (maximize) -w else w
            }

    override fun improvement(labeling: Labeling, ix: Literal, propagations: Literals): Double {
        return improvementLiteral(labeling, !labeling.literal(ix)) + propagations.sumByDouble {
            improvementLiteral(labeling, it)
        }
    }
}

/**
 * Used to turn an [Optimizer] into a boolean sat [Solver].
 */
object SatObjective : ObjectiveFunction {
    override fun value(labeling: Labeling) = 0.0
    override fun lowerBound() = 0.0
    override fun improvement(labeling: Labeling, ix: Literal, propagations: Literals) = 0.0
    override fun penalty(violations: Int) = violations.toDouble()
}

fun Optimizer<SatObjective>.toSolver(): Solver = object : Solver {
    override fun witnessOrThrow(assumptions: Literals) = optimizeOrThrow(SatObjective, assumptions)
}

fun Optimizer<LinearObjective>.toSolver(problem: Problem, randomSeed: Long = nanos()): Solver = object : Solver {
    val randomSequence = RandomSequence(randomSeed)

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        val rng = randomSequence.next()
        return optimizeOrThrow(
                LinearObjective(false, DoubleArray(problem.nbrVariables) { rng.nextDouble() - 0.5 }), assumptions)
    }
}
