package combo.sat.solvers

import combo.sat.Problem

class JacopSolverTest : SolverTest() {
    override fun solver(problem: Problem) = JacopSolver(problem)
    override fun numericSolver(problem: Problem) = null
    override fun timeoutSolver(problem: Problem) = null
    override fun largeSolver(problem: Problem) = null
}

class JacopLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = JacopSolver(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun timeoutOptimizer(problem: Problem) = null
}
