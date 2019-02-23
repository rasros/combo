package combo.model

import combo.bandit.*
import combo.bandit.glm.NormalVariance
import combo.bandit.glm.VarianceFunction
import combo.bandit.univariate.BanditPolicy
import combo.math.Loss
import combo.math.SquaredLoss
import combo.math.Transform
import combo.math.VarianceEstimator
import combo.sat.solvers.*
import combo.util.EMPTY_INT_ARRAY
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

class ModelOptimizer<D>(val model: Model, val bandit: Bandit<D>) {

    companion object {

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> combinatorialBandit(model: Model,
                                                        banditPolicy: BanditPolicy<E>,
                                                        solver: Solver =
                                                                if (model.problem.nbrVariables <= 15) ExhaustiveSolver(model.problem)
                                                                else LocalSearchSolver(model.problem),
                                                        limit: Int = 500): ModelOptimizer<Array<LabelingData<E>>> {
            val bandits = solver.sequence().take(limit).toList().toTypedArray()
            val bandit = CombinatorialBandit(bandits, banditPolicy)
            return ModelOptimizer(model, bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> dtBandit(model: Model,
                                             banditPolicy: BanditPolicy<E>,
                                             solver: Solver = FallbackSolver(LocalSearchSolver(model.problem)))
                : ModelOptimizer<Array<LiteralData<E>>> {
            val bandit = DecisionTreeBandit(model.problem, banditPolicy)
            return ModelOptimizer(model, bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun glmBandit(model: Model,
                      family: VarianceFunction = NormalVariance,
                      link: Transform = family.canonicalLink(),
                      regularization: Loss = SquaredLoss,
                      linearOptimizer: Optimizer<LinearObjective> =
                              FallbackOptimizer(LocalSearchOptimizer<LinearObjective>(model.problem).apply { restarts = 1 }))
                : ModelOptimizer<DoubleArray> {
            TODO()
        }

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> gaBandit(model: Model,
                                             banditPolicy: BanditPolicy<E>,
                                             solver: Solver = FallbackSolver(LocalSearchSolver(model.problem))): ModelOptimizer<Array<LabelingData<E>>> {
            TODO()
        }
    }

    @JvmOverloads
    fun choose(assumptions: Map<Feature<*>, Any?> = emptyMap()): Assignment? {
        val instance = bandit.choose(assumptionsLiterals(assumptions))
        return if (instance != null) model.toAssignment(instance)
        else null
    }

    @JvmOverloads
    fun chooseOrThrow(assumptions: Map<Feature<*>, Any?> = emptyMap()) =
            model.toAssignment(bandit.chooseOrThrow(assumptionsLiterals(assumptions)))

    @JvmOverloads
    fun update(assignment: Assignment, result: Double, weight: Double = 1.0) {
        bandit.update(assignment.instance, result, weight)
    }

    private fun assumptionsLiterals(assumptions: Map<Feature<*>, Any?>): IntArray {
        return if (assumptions.isNotEmpty()) {
            var lits = IntArray(8) { -1 }
            var itr = 0
            for ((meta, value) in assumptions) {
                val add = model.featureMetas[meta]?.indexEntry?.toLiterals(value)
                if (add != null && add.isNotEmpty()) {
                    if (lits.size <= itr + add.size)
                        lits += IntArray(lits.size) { -1 }
                    for (i in 0 until add.size)
                        lits[itr + i] = add[i]
                    itr += add.size
                }
            }
            val firstNeg = lits.indexOfFirst { it == -1 }
            if (firstNeg > 0)
                lits = lits.sliceArray(0 until firstNeg)
            lits.sort()
            lits
        } else EMPTY_INT_ARRAY
    }
}
