package combo.model


import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.LocalSearchSolver
import combo.sat.solvers.Solver
import combo.util.EmptyCollection
import combo.util.IntCollection
import combo.util.IntHashSet
import kotlin.jvm.JvmStatic

class ModelSolver(val model: Model, val solver: Solver) {

    companion object {
        @JvmStatic
        fun exhaustive(model: Model) = ModelSolver(model, ExhaustiveSolver(model.problem))

        @JvmStatic
        fun localSearch(model: Model) = ModelSolver(model, LocalSearchSolver(model.problem))
    }

    fun witnessOrThrow(vararg assumptions: Literal) =
            model.toAssignment(solver.witnessOrThrow(assumptionsLiterals(assumptions)))

    fun witness(vararg assumptions: Literal): Assignment? {
        val instance = solver.witness(assumptionsLiterals(assumptions))
        return if (instance != null) model.toAssignment(instance)
        else null
    }

    fun asSequence(vararg assumptions: Literal) =
            model.toAssignments(solver.asSequence(assumptionsLiterals(assumptions)))

    private fun assumptionsLiterals(assumptions: Array<out Literal>): IntCollection {
        if (assumptions.isEmpty()) return EmptyCollection
        val set = IntHashSet()
        assumptions.forEach { it.collectLiterals(model.index, set) }
        return set
    }
}
