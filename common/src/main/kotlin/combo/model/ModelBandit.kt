package combo.model

import combo.bandit.*
import combo.bandit.ga.GABandit
import combo.bandit.glm.NormalVariance
import combo.bandit.glm.VarianceFunction
import combo.bandit.univariate.BanditPolicy
import combo.math.*
import combo.sat.FastRandomSet
import combo.sat.ImplicationConstraintCoercer
import combo.sat.WeightSet
import combo.sat.solvers.*
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntHashSet
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

class ModelBandit<D>(val model: Model, val bandit: Bandit<D>) {

    companion object {

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> combinatorialBandit(model: Model,
                                                        banditPolicy: BanditPolicy<E>,
                                                        solver: Solver =
                                                                if (model.problem.nbrVariables <= 15) ExhaustiveSolver(model.problem)
                                                                else LocalSearchSolver(model.problem),
                                                        limit: Int = 500): ModelBandit<Array<InstanceData<E>>> {
            val bandits = solver.asSequence().take(limit).toList().toTypedArray()
            val bandit = CombinatorialBandit(bandits, banditPolicy)
            return ModelBandit(model, bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> dtBandit(model: Model,
                                             banditPolicy: BanditPolicy<E>,
                                             solver: Solver = CachedSolver(LocalSearchSolver(model.problem).apply {
                                                 initializer = ImplicationConstraintCoercer(model.problem, ImplicationDigraph(problem), FastRandomSet())
                                             }).apply { pNew = 0.5f })
                : ModelBandit<Array<LiteralData<E>>> {
            val bandit = DecisionTreeBandit(model.problem, banditPolicy)
            return ModelBandit(model, bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun glmBandit(model: Model,
                      family: VarianceFunction = NormalVariance,
                      link: Transform = family.canonicalLink(),
                      regularization: Loss = SquaredLoss,
                      linearOptimizer: Optimizer<LinearObjective> =
                              CachedOptimizer(LocalSearchOptimizer<LinearObjective>(model.problem).apply {
                                  restarts = 1
                                  initializer = ImplicationConstraintCoercer(model.problem, ImplicationDigraph(problem), WeightSet(0.2f))
                              }).apply { this.pNew = 0.5f })
                : ModelBandit<FloatArray> {
            TODO()
        }

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> gaBandit(model: Model,
                                             banditPolicy: BanditPolicy<E>,
                                             solver: Solver = CachedSolver(LocalSearchSolver(model.problem).apply {
                                                 initializer = ImplicationConstraintCoercer(model.problem, ImplicationDigraph(problem), FastRandomSet())
                                             }).apply { pNew = 1.0f })
                : ModelBandit<Array<InstanceData<E>>> {
            val bandit = GABandit(model.problem, banditPolicy, solver)
            return ModelBandit(model, bandit)
        }
    }

    fun choose(vararg assumptions: Literal): Assignment? {
        val instance = bandit.choose(assumptionsLiterals(assumptions))
        return if (instance != null) model.toAssignment(instance)
        else null
    }

    fun chooseOrThrow(vararg assumptions: Literal) =
            model.toAssignment(bandit.chooseOrThrow(assumptionsLiterals(assumptions)))

    @JvmOverloads
    fun update(assignment: Assignment, result: Float, weight: Float = 1.0f) {
        bandit.update(assignment.instance, result, weight)
    }

    private fun assumptionsLiterals(assumptions: Array<out Literal>): IntArray {
        if (assumptions.isEmpty()) return EMPTY_INT_ARRAY
        val set = IntHashSet()
        assumptions.forEach { it.toAssumption(model.index, set) }
        return set.toArray().apply { sort() }
    }
}
