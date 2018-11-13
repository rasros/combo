package combo.sat.solvers

import combo.sat.Problem

class JOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig) = JOptimizer(problem, config)
    override fun largeOptimizer(problem: Problem, config: SolverConfig) = JOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig) = JOptimizer(problem, config, maxItr = 10)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig) = JOptimizer(problem, config, maxItr = 1)
}
