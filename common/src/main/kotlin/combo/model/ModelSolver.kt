package combo.model


import combo.sat.solvers.Solver
import combo.util.EMPTY_INT_ARRAY
import combo.util.EmptyCollection
import combo.util.IntCollection
import combo.util.IntHashSet

class ModelSolver(val model: Model, val solver: Solver) {

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
        assumptions.forEach { it.toAssumption(model.index, set) }
        return set
    }
}
