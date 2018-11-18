package combo.sat.solvers

import combo.sat.ExtendedProblem

class WalkSatTest : SolverTest() {
    override fun solver(problem: ExtendedProblem) = WalkSat(problem.problem)
    override fun largeSolver(problem: ExtendedProblem) = null
    override fun unsatSolver(problem: ExtendedProblem) = WalkSat(problem.problem, maxFlips = 10, maxRestarts = 1)
    override fun timeoutSolver(problem: ExtendedProblem) = WalkSat(problem.problem, timeout = 1L, maxConsideration = 1, maxFlips = 1, maxRestarts = 1)
}
