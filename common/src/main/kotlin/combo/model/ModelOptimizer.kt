package combo.model

import combo.bandit.Bandit
import combo.sat.solvers.Solver
import combo.util.EMPTY_INT_ARRAY
import kotlin.jvm.JvmOverloads

class ModelOptimizer<D>(val model: Model, val solver: Solver, val bandit: Bandit<D>) : Iterable<Assignment> {
    companion object {

        /*
        TODO
        private fun defaultSolver(p: Problem, pt: BinaryPropagationGraph?): Solver =
                LocalSearchSolver(p, pt, maxRestarts = Int.MAX_VALUE)

        private fun defaultLinOpt(p: Problem, c: SolverConfig, pt: BinaryPropagationGraph?): Optimizer<LinearObjective> =
                FallbackOptimizer(LocalSearchOptimizer(p, c, pt, restarts = 1))

        @JvmStatic
        @JvmOverloads
        fun combinatorialBandit(model: Model,
                                config: SolverConfig = SolverConfig(maximize = true),
                                posterior: UnivariatePosterior = NormalPosterior,
                                solver: Solver = ExhaustiveSolver(model.problem, config)): ModelOptimizer {
            if (model.problem.nbrVariables >= 20)
                throw IllegalArgumentException("Multi-armed bandit algorithm will not work with excessive number of variables (>=20).")
            val bandits = solver.sequence().toList().toTypedArray()
            val bandit = CombinatorialBandit(bandits, config, posterior)
            return ModelOptimizer(model, PresolvedSolver(bandits, config), bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun dtBandit(model: Model,
                     config: SolverConfig = SolverConfig(maximize = true),
                     posterior: UnivariatePosterior = NormalPosterior,
                     solver: Solver = defaultSolver(model.problem, config, BinaryPropagationGraph(model.problem))): ModelOptimizer {
            val bandit = DecisionTreeBandit(model.problem, config, solver, posterior)
            return ModelOptimizer(model, solver, bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun glmBandit(model: Model,
                      config: SolverConfig = SolverConfig(maximize = true),
                      family: VarianceFunction = GaussianVariance,
                      link: Transform = family.canonicalLink(),
                      regularization: Loss = SquaredLoss,
                      propagationGraph: BinaryPropagationGraph? = BinaryPropagationGraph(model.problem),
                      linearOptimizer: Optimizer<LinearObjective> = defaultLinOpt(model.problem, config, propagationGraph),
                      solver: Solver = defaultSolver(model.problem, config, propagationGraph)): ModelOptimizer {
            TODO()
        }

        @JvmStatic
        @JvmOverloads
        fun gaBandit(model: Model,
                     config: SolverConfig = SolverConfig(maximize = true),
                     propagationGraph: BinaryPropagationGraph?,
                     posterior: UnivariatePosterior? = null,
                     solver: Solver = defaultSolver(model.problem, config, propagationGraph)): ModelOptimizer {
            TODO()
        }
         */
    }

    @JvmOverloads
    fun witness(assumptions: Map<Feature<*>, Any?> = emptyMap()): Assignment? {
        val l = solver.witness(assumptionsLiterals(assumptions))
        if (l != null) return model.toAssignment(l)
        return null
    }

    @JvmOverloads
    fun witnessOrThrow(assumptions: Map<Feature<*>, Any?> = emptyMap()) =
            model.toAssignment(solver.witnessOrThrow(assumptionsLiterals(assumptions)))

    @JvmOverloads
    fun sequence(assumptions: Map<Feature<*>, Any?> = emptyMap()) =
            solver.sequence(assumptionsLiterals(assumptions)).map { model.toAssignment(it) }

    override fun iterator(): Iterator<Assignment> = sequence().iterator()

    @JvmOverloads
    fun choose(assumptions: Map<Feature<*>, Any?> = emptyMap()): Assignment? {
        val labeling = bandit.choose(assumptionsLiterals(assumptions))
        return if (labeling != null) model.toAssignment(labeling)
        else null
    }

    @JvmOverloads
    fun chooseOrThrow(assumptions: Map<Feature<*>, Any?> = emptyMap()) =
            model.toAssignment(bandit.chooseOrThrow(assumptionsLiterals(assumptions)))

    @JvmOverloads
    fun update(assignment: Assignment, result: Double, weight: Double = 1.0) {
        bandit.update(assignment.labeling, result, weight)
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
