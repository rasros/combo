package combo.sat.solvers

import combo.sat.Problem
import combo.sat.UnitPropagationTable

class PresolvedSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            PresolvedSolver(ExhaustiveSolverTest().solver(problem, propTable).sequence().toList().toTypedArray())

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) = null

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            PresolvedSolver(ExhaustiveSolverTest().unsatSolver(problem, propTable).sequence().toList().toTypedArray())

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            PresolvedSolver(ExhaustiveSolverTest().timeoutSolver(problem, propTable).sequence().toList().toTypedArray())
}
