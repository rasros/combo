package combo.sat

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem) = CachedSolver(WalkSat(problem))
    override fun unsatSolver(problem: Problem) = CachedSolver(WalkSatTest().unsatSolver(problem))
    override fun timeoutSolver(problem: Problem) = CachedSolver(WalkSatTest().timeoutSolver(problem))
}
