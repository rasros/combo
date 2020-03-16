package combo.sat.optimizers

import combo.math.EMPTY_VECTOR
import combo.math.VectorView
import combo.model.EffectCodedVector
import combo.model.Model
import combo.sat.Instance
import combo.sat.literal
import combo.sat.not
import combo.sat.toIx


interface ObjectiveFunction {
    /**
     * Value to minimize evaluated on a [VectorView], which take on values between zeros and ones.
     */
    fun value(vector: VectorView): Float

    /**
     * Optionally implemented. Optimal bound on function, if reached during search the algorithm will terminate immediately.
     */
    fun lowerBound(): Float = Float.NEGATIVE_INFINITY

    fun upperBound(): Float = Float.POSITIVE_INFINITY

    /**
     * Override for efficiency reasons. New value should be previous value - improvement.
     */
    fun improvement(instance: Instance, ix: Int): Float {
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
open class LinearObjective(val maximize: Boolean, val weights: VectorView) : ObjectiveFunction {
    override fun value(vector: VectorView) = (vector dot weights).let {
        if (maximize) -it else it
    }
}

open class DeltaLinearObjective(maximize: Boolean, weights: VectorView) : LinearObjective(maximize, weights) {
    override fun improvement(instance: Instance, ix: Int): Float {
        val literal = !instance.literal(ix)
        return if (instance.literal(literal.toIx()) == literal) 0.0f
        else {
            val w = weights[literal.toIx()].let { if (instance.isSet(literal.toIx())) it else -it }
            if (maximize) -w else w
        }
    }
}

object SatObjective : LinearObjective(false, EMPTY_VECTOR) {
    override fun value(vector: VectorView) = 0.0f
    override fun lowerBound() = 0.0f
    override fun upperBound() = 0.0f
    override fun improvement(instance: Instance, ix: Int) = 0.0f
}

class EffectCodedObjective(val base: ObjectiveFunction, val model: Model) : ObjectiveFunction {
    override fun value(vector: VectorView) = base.value(EffectCodedVector(model, vector as Instance))
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

class StatisticObjectiveFunction(val base: ObjectiveFunction) : ObjectiveFunction by base {
    var functionEvaluations = 0
        private set
    var improvementEvaluations = 0
        private set

    override fun value(vector: VectorView): Float {
        functionEvaluations++
        return base.value(vector)
    }

    override fun improvement(instance: Instance, ix: Int): Float {
        improvementEvaluations++
        return base.improvement(instance, ix)
    }
}