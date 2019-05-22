package combo.model

import combo.bandit.Bandit
import combo.bandit.CombinatorialBandit
import combo.bandit.DecisionTreeBandit
import combo.bandit.PredictionBandit
import combo.bandit.ga.GeneticAlgorithmBandit
import combo.bandit.univariate.BanditPolicy
import combo.math.DataSample
import combo.math.ImplicationDigraph
import combo.math.VarianceEstimator
import combo.sat.ImplicationConstraintCoercer
import combo.sat.WordRandomSet
import combo.sat.solvers.CachedSolver
import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.LocalSearchSolver
import combo.sat.solvers.Solver
import combo.util.EmptyCollection
import combo.util.IntCollection
import combo.util.IntHashSet
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

open class ModelBandit<B : Bandit<*>>(val model: Model, open val bandit: B) {

    companion object {

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> combinatorialBandit(model: Model,
                                                        banditPolicy: BanditPolicy<E>,
                                                        solver: Solver =
                                                                if (model.problem.nbrVariables <= 15) ExhaustiveSolver(model.problem)
                                                                else LocalSearchSolver(model.problem),
                                                        limit: Int = 500): ModelBandit<CombinatorialBandit<E>> {
            val bandits = solver.asSequence().take(limit).toList().toTypedArray()
            val bandit = CombinatorialBandit(bandits, banditPolicy)
            return ModelBandit(model, bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> treeBandit(model: Model,
                                               banditPolicy: BanditPolicy<E>,
                                               solver: Solver = CachedSolver(LocalSearchSolver(model.problem).apply {
                                                   initializer = ImplicationConstraintCoercer(model.problem, ImplicationDigraph(problem), WordRandomSet())
                                               }).apply { pNew = 0.5f })
                : PredictionModelBandit<DecisionTreeBandit<E>> {
            val bandit = DecisionTreeBandit(model.problem, banditPolicy, solver)
            return PredictionModelBandit(model, bandit)
        }

        /*
        @JvmStatic
        @JvmOverloads
        fun linearBandit(model: Model,
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
        }*/

        @JvmStatic
        @JvmOverloads
        fun <E : VarianceEstimator> geneticAlgorithmBandit(model: Model,
                                                           banditPolicy: BanditPolicy<E>,
                                                           solver: Solver = CachedSolver(LocalSearchSolver(model.problem).apply {
                                                               initializer = ImplicationConstraintCoercer(model.problem, ImplicationDigraph(problem), WordRandomSet())
                                                           }).apply { pNew = 1.0f })
                : ModelBandit<GeneticAlgorithmBandit<E>> {
            val bandit = GeneticAlgorithmBandit(model.problem, banditPolicy, solver)
            // Extract implication digraph if possible
            bandit.implicationDigraph = (((solver as? CachedSolver)?.baseSolver as? LocalSearchSolver)?.initializer as? ImplicationConstraintCoercer)?.implicationDigraph
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

    private fun assumptionsLiterals(assumptions: Array<out Literal>): IntCollection {
        if (assumptions.isEmpty()) return EmptyCollection
        val set = IntHashSet()
        assumptions.forEach { it.toAssumption(model.index, set) }
        return set
    }
}

class PredictionModelBandit<B : PredictionBandit<*>>(model: Model, override val bandit: B) : ModelBandit<B>(model, bandit) {
    /**
     * The total absolute error obtained on a prediction before update.
     */
    var trainAbsError: DataSample = bandit.trainAbsError

    /**
     * The total absolute error obtained on a prediction after update.
     */
    var testAbsError: DataSample = bandit.testAbsError

    /**
     * Evaluate the machine learning model on a [Assignment].
     */
    fun predict(assignment: Assignment): Float = bandit.predict(assignment.instance)
}
