package combo.sat

import combo.math.Vector
import combo.model.ValidationException
import combo.util.EMPTY_INT_ARRAY

interface LinearOptimizer {
    fun optimize(weights: Vector, contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            optimizeOrThrow(weights, contextLiterals)
        } catch (e: ValidationException) {
            null
        }
    }

    fun optimizeOrThrow(weights: Vector, contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling

    val config: SolverConfig
}
