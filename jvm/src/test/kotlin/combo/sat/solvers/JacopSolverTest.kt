package combo.sat.solvers

import combo.sat.Problem

class JacopSolverTest : SolverTest() {
    override fun solver(problem: Problem) = JacopSolver(problem)
    override fun timeoutSolver(problem: Problem) = JacopSolver(problem).apply { timeout = 1L }
    override fun largeSolver(problem: Problem) = null
}

class JacopLinearOptimizerTest : LinearOptimizerTest() {
    override val isComplete = true
    override fun optimizer(problem: Problem) = JacopSolver(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun timeoutOptimizer(problem: Problem) = JacopSolver(problem).apply { timeout = 1L }
}
