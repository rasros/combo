package combo.sat.solvers

import combo.sat.Problem

class WalkSatTest : SolverTest() {
    override fun solver(problem: Problem) = WalkSat(problem)
    override fun largeSolver(problem: Problem) = WalkSat(problem)
    override fun unsatSolver(problem: Problem) = WalkSat(problem, maxFlips = 10, maxRestarts = 1)
    override fun timeoutSolver(problem: Problem) = WalkSat(problem, timeout = 1L, maxConsideration = 1, maxFlips = 1, maxRestarts = 1)
}
