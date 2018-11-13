package combo.sat.solvers

import combo.sat.Problem

class ExhaustiveSolverTest : SolverTest() {
    override fun solver(problem: Problem) = ExhaustiveSolver(problem)
    override fun largeSolver(problem: Problem) = null
    override fun unsatSolver(problem: Problem) = ExhaustiveSolver(problem, timeout = 1L)
    override fun timeoutSolver(problem: Problem) = ExhaustiveSolver(problem, timeout = 1L)
}
