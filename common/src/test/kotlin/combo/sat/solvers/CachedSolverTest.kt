package combo.sat.solvers

import combo.sat.ExtendedProblem

class CachedSolverTest : SolverTest() {
    override fun solver(problem: ExtendedProblem) = CachedSolver(WalkSatTest().solver(problem), pEviction = 0.95)
    override fun largeSolver(problem: ExtendedProblem) = null // TODO use fast walksat CachedSolver(WalkSatTest().solver(problem), pEviction = 0.95)
    override fun unsatSolver(problem: ExtendedProblem) = CachedSolver(WalkSatTest().unsatSolver(problem), pEviction = 0.95)
    override fun timeoutSolver(problem: ExtendedProblem) = CachedSolver(WalkSatTest().timeoutSolver(problem), pEviction = 0.95)
}
