package combo.sat.optimizers

import combo.math.Vector
import combo.sat.*
import combo.util.EMPTY_FLOAT_ARRAY
import combo.util.sumByFloat
import kotlin.math.max
import kotlin.math.min


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

    override fun improvement(instance: MutableInstance, ix: Int): Float {
        val literal = !instance.literal(ix)
        return if (instance.literal(literal.toIx()) == literal) 0.0f
        else {
            val w = weights[literal.toIx()].let { if (instance[literal.toIx()]) it else -it }
            if (maximize) -w else w
        }
    }
}

object SatObjective : LinearObjective(false, EMPTY_FLOAT_ARRAY) {
    override fun value(instance: Instance) = 0.0f
    override fun lowerBound() = 0.0f
    override fun upperBound() = 0.0f
    override fun improvement(instance: MutableInstance, ix: Int) = 0.0f
}

/**
 * Exterior penalty added to objective used by genetic algorithms.
 * For information about possibilities, see:
 * Penalty Function Methods for Constrained Optimization with Genetic Algorithms
 * https://doi.org/10.3390/mca10010045
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

/**
 * This penalty ensures that any infeasible candidate solution has a penalized
 * score that is strictly greater than a feasible solution. In order for that to work the
 * [combo.sat.optimizers.ObjectiveFunction.lowerBound] and [combo.sat.optimizers.ObjectiveFunction.upperBound] must be
 * implemented and be finite. Otherwise, choose another penalty function that does not rely on bounds.
 * */
class DisjunctPenalty(private val extended: PenaltyFunction = LinearPenalty()) : PenaltyFunction {
    override fun penalty(value: Float, violations: Int, lowerBound: Float, upperBound: Float): Float {
        return if (violations > 0) upperBound - lowerBound + extended.penalty(value, violations, lowerBound, upperBound)
        else 0.0f
    }
}