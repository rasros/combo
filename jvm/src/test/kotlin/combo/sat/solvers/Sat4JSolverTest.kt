package combo.sat.solvers

import combo.sat.Problem
import combo.sat.optimizers.LinearOptimizer

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: Problem) = Sat4JSolver(problem)
    override fun largeSolver(problem: Problem) = Sat4JSolver(problem)
    override fun unsatSolver(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutSolver(problem: Problem) = Sat4JSolver(problem, timeout = 1L)
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config)
    override fun largeOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config, timeout = 1L)
}
