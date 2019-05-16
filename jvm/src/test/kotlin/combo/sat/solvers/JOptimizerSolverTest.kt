package combo.sat.solvers

import combo.sat.Problem

class JOptimizerLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = JOptimizerSolver(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun infeasibleOptimizer(problem: Problem) = JOptimizerSolver(problem).apply { maxIterations = 1 }
}
