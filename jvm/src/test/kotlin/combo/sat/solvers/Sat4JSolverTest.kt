package combo.sat.solvers

import combo.sat.ExtendedProblem
import combo.sat.Problem
import combo.sat.optimizers.LinearOptimizer
import combo.sat.optimizers.LinearOptimizerTest

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: ExtendedProblem) = Sat4JSolver(problem.problem)
    override fun largeSolver(problem: ExtendedProblem) = Sat4JSolver(problem.problem)
    override fun unsatSolver(problem: ExtendedProblem) = Sat4JSolver(problem.problem)
    override fun timeoutSolver(problem: ExtendedProblem) = Sat4JSolver(problem.problem, timeout = 1L)
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config)
    override fun largeOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JLinearOptimizer(problem, config, timeout = 1L)
}
