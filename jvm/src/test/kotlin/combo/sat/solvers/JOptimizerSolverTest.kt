package combo.sat.solvers

import combo.sat.Problem

class JOptimizerLinearOptimizerTest : LinearOptimizerTest() {
    override val isComplete = true
    override fun optimizer(problem: Problem) = JOptimizer(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun infeasibleOptimizer(problem: Problem) = JOptimizer(problem).apply { maxIterations = 1 }
}
