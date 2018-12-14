package combo.sat.solvers

import combo.sat.Problem
import combo.sat.UnitPropagationTable

class ExhaustiveSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) = ExhaustiveSolver(problem)
    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) = null
    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) = ExhaustiveSolver(problem, timeout = 1L)
    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) = ExhaustiveSolver(problem, timeout = 1L)
}
