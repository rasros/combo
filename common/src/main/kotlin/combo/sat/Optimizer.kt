@file:JvmName("Optimizers")

package combo.sat

import combo.math.Vector
import combo.sat.optimizers.LinearOptimizer
import combo.util.EMPTY_INT_ARRAY
import kotlin.jvm.JvmName

interface Optimizer {

    interface ObjectiveFunction {
        fun value(labeling: Labeling): Double
    }

    fun optimize(function: ObjectiveFunction, contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            optimizeOrThrow(function, contextLiterals)
        } catch (e: ValidationException) {
            null
        }
    }

    fun optimizeOrThrow(function: ObjectiveFunction, contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling

    val config: SolverConfig

    fun asLinear() = object : LinearOptimizer {
        override fun optimize(weights: Vector, contextLiterals: Literals): Labeling? {
            val f = object : ObjectiveFunction {
                override fun value(labeling: Labeling) = labeling dot weights
            }
            return optimize(f, contextLiterals)
        }

        override fun optimizeOrThrow(weights: Vector, contextLiterals: Literals): Labeling {
            val f = object : ObjectiveFunction {
                override fun value(labeling: Labeling) = labeling dot weights
            }
            return optimizeOrThrow(f, contextLiterals)
        }

        override val config: SolverConfig
            get() = this@Optimizer.config
    }
}

