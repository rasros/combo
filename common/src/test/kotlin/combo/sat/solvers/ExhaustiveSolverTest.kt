package combo.sat.solvers

import combo.sat.ExtendedProblem

class ExhaustiveSolverTest : SolverTest() {
    override fun solver(problem: ExtendedProblem) = ExhaustiveSolver(problem.problem)
    override fun largeSolver(problem: ExtendedProblem) = null
    override fun unsatSolver(problem: ExtendedProblem) = ExhaustiveSolver(problem.problem, timeout = 1L)
    override fun timeoutSolver(problem: ExtendedProblem) = ExhaustiveSolver(problem.problem, timeout = 1L)
}
