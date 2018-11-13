package combo.sat.solvers

import combo.sat.Problem

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem) = CachedSolver(WalkSatTest().solver(problem), pEviction = 0.95)
    override fun largeSolver(problem: Problem) = CachedSolver(WalkSatTest().solver(problem), pEviction = 0.95)
    override fun unsatSolver(problem: Problem) = CachedSolver(WalkSatTest().unsatSolver(problem), pEviction = 0.95)
    override fun timeoutSolver(problem: Problem) = CachedSolver(WalkSatTest().timeoutSolver(problem), pEviction = 0.95)
}
