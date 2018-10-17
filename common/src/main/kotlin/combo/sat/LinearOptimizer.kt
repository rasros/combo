package combo.sat

import combo.math.Vector
import combo.model.ValidationException

interface LinearOptimizer {
    fun optimize(weights: Vector, contextLiterals: Literals = intArrayOf()): Labeling? {
        return try {
            optimizeOrThrow(weights, contextLiterals)
        } catch (e: ValidationException) {
            null
        }
    }

    fun optimizeOrThrow(weights: Vector, contextLiterals: Literals = intArrayOf()): Labeling

    val config: SolverConfig
}
