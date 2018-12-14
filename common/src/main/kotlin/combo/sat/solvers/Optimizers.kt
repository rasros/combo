package combo.sat.solvers

import combo.math.Vector
import combo.sat.*
import combo.util.EMPTY_INT_ARRAY

interface Optimizer<O : ObjectiveFunction> {

    fun optimize(function: O, contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            optimizeOrThrow(function, contextLiterals)
        } catch (e: ValidationException) {
            null
        }
    }

    fun optimizeOrThrow(function: O, contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling

    val config: SolverConfig

}

class LinearObjective(val weights: Vector) : ObjectiveFunction {
    override fun value(labeling: Labeling) = labeling dot weights
}

interface ObjectiveFunction {
    fun value(labeling: Labeling): Double
}

fun ObjectiveFunction.score(labeling: Labeling, maximize: Boolean) =
        value(labeling).let { if (maximize) it else -it }

fun ObjectiveFunction.score(labeling: Labeling, contextLiterals: Literals, problem: Problem?,
                            upperBound: Double, maximize: Boolean): Double {
    val penalty = (problem?.flipsToSatisfy(labeling) ?: 0) + Conjunction(contextLiterals).flipsToSatisfy(labeling)
    val v = value(labeling)
    val r = if (penalty > 0) penalty + upperBound + v else v
    return if (maximize) r else -r
}
