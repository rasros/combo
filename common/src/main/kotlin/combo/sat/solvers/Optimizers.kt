package combo.sat.solvers

import combo.math.Vector
import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.ValidationException
import combo.sat.dot
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

interface Optimizer<O : ObjectiveFunction> {

    fun optimize(function: O, assumptions: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            optimizeOrThrow(function, assumptions)
        } catch (e: ValidationException) {
            null
        }
    }

    fun optimizeOrThrow(function: O, assumptions: Literals = EMPTY_INT_ARRAY): Labeling

    val config: SolverConfig

}

class LinearObjective(val weights: Vector) : ObjectiveFunction {
    override fun upperBound(): Double = weights.sumByDouble { max(0.0, it) }
    override fun lowerBound(): Double = weights.sumByDouble { min(0.0, it) }
    override fun value(labeling: Labeling) = labeling dot weights
}

interface ObjectiveFunction {
    fun value(labeling: Labeling): Double
    fun upperBound(): Double = Double.MAX_VALUE
    fun lowerBound(): Double = -Double.MAX_VALUE
}

fun ObjectiveFunction.value(labeling: Labeling, maximize: Boolean) =
        value(labeling).let { if (maximize) it else -it }

fun ObjectiveFunction.upperBound(maximize: Boolean): Double = if (maximize) upperBound() else -lowerBound(true)
fun ObjectiveFunction.lowerBound(maximize: Boolean): Double = if (maximize) lowerBound() else -upperBound(true)

fun ObjectiveFunction.value(labeling: Labeling, constraintPenalty: Int, lowerBound: Double, upperBound: Double, maximize: Boolean): Double {
    val f = if (maximize) value(labeling) else -value(labeling)
    return if (constraintPenalty > 0) {
        val dist = abs(upperBound - lowerBound)
        return lowerBound - dist + f - constraintPenalty
    } else f
}
