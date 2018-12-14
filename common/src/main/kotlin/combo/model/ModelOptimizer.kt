package combo.model

import combo.bandit.Bandit
import combo.bandit.DecisionTreeBandit
import combo.bandit.MultiArmedBandit
import combo.bandit.glm.GaussianVariance
import combo.bandit.glm.VarianceFunction
import combo.math.*
import combo.sat.Problem
import combo.sat.SolverConfig
import combo.sat.UnitPropagationTable
import combo.sat.solvers.*
import combo.util.EMPTY_INT_ARRAY
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

class ModelOptimizer(val model: Model, val solver: Solver, val bandit: Bandit) : Iterable<Assignment> {
    companion object {

        private fun defaultSolver(p: Problem, c: SolverConfig, pt: UnitPropagationTable?): Solver =
                LocalSearchSolver(p, c, pt, maxRestarts = Int.MAX_VALUE)

        private fun defaultLinOpt(p: Problem, c: SolverConfig, pt: UnitPropagationTable?): Optimizer<LinearObjective> =
                FallbackOptimizer(LocalSearchOptimizer(p, c, pt, maxRestarts = 1))

        @JvmStatic
        @JvmOverloads
        fun combinatorialBandit(model: Model,
                                config: SolverConfig = SolverConfig(maximize = true),
                                posterior: Posterior = NormalPosterior,
                                solver: Solver = ExhaustiveSolver(model.problem, config)): ModelOptimizer {
            if (model.problem.nbrVariables >= 20)
                throw IllegalArgumentException("Multi-armed bandit algorithm will not work with excessive number of variables (>=20).")
            val bandits = solver.sequence().toList().toTypedArray()
            val bandit = MultiArmedBandit(bandits, config, posterior)
            return ModelOptimizer(model, PresolvedSolver(bandits, config), bandit)
        }

        @JvmStatic
        @JvmOverloads
        fun dtBandit(model: Model,
                     config: SolverConfig = SolverConfig(maximize = true),
                     posterior: Posterior = NormalPosterior,
                     solver: Solver = defaultSolver(model.problem, config, UnitPropagationTable(model.problem))): ModelOptimizer {
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
                      propagationTable: UnitPropagationTable? = UnitPropagationTable(model.problem),
                      linearOptimizer: Optimizer<LinearObjective> = defaultLinOpt(model.problem, config, propagationTable),
                      solver: Solver = defaultSolver(model.problem, config, propagationTable)): ModelOptimizer {
            TODO()
        }

        @JvmStatic
        @JvmOverloads
        fun gaBandit(model: Model,
                     config: SolverConfig = SolverConfig(maximize = true),
                     propagationTable: UnitPropagationTable?,
                     posterior: Posterior? = null,
                     solver: Solver = defaultSolver(model.problem, config, propagationTable)): ModelOptimizer {
            TODO()
        }
    }

    @JvmOverloads
    fun witness(context: Map<Feature<*>, Any?> = emptyMap()): Assignment? {
        val l = solver.witness(contextLiterals(context))
        if (l != null) return model.toAssignment(l)
        return null
    }

    @JvmOverloads
    fun witnessOrThrow(context: Map<Feature<*>, Any?> = emptyMap()) =
            model.toAssignment(solver.witnessOrThrow(contextLiterals(context)))

    @JvmOverloads
    fun sequence(context: Map<Feature<*>, Any?> = emptyMap()) =
            solver.sequence(contextLiterals(context)).map { model.toAssignment(it) }

    override fun iterator(): Iterator<Assignment> = sequence().iterator()

    @JvmOverloads
    fun choose(context: Map<Feature<*>, Any?> = emptyMap()): Assignment? {
        val labeling = bandit.choose(contextLiterals(context))
        return if (labeling != null) model.toAssignment(labeling)
        else null
    }

    @JvmOverloads
    fun chooseOrThrow(context: Map<Feature<*>, Any?> = emptyMap()) =
            model.toAssignment(bandit.chooseOrThrow(contextLiterals(context)))

    @JvmOverloads
    fun update(assignment: Assignment, result: Double, weight: Double = 1.0) {
        bandit.update(assignment.labeling, result, weight)
    }

    private fun contextLiterals(context: Map<Feature<*>, Any?>): IntArray {
        return if (context.isNotEmpty()) {
            var lits = IntArray(8) { -1 }
            var itr = 0
            for ((meta, value) in context) {
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
