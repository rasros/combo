package combo.sat

class ExhaustiveSolverTest : SolverTest() {
    override fun solver(problem: Problem) = ExhaustiveSolver(problem)
    override fun unsatSolver(problem: Problem) = ExhaustiveSolver(problem, timeout = 1L)
    override fun timeoutSolver(problem: Problem) = ExhaustiveSolver(problem, timeout = 1L)
}
