package combo.sat.solvers

import combo.sat.Problem

class JacopSolverTest : SolverTest, LinearOptimizerTest {
    override fun solver(problem: Problem) = JacopSolver(problem)
    override fun timeoutSolver(problem: Problem) = null //JacopSolver(problem, timeout = 1L)
    override fun optimizer(problem: Problem) = JacopSolver(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun timeoutOptimizer(problem: Problem) = null
}
