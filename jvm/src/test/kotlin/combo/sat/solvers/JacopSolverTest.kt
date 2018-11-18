package combo.sat.solvers

import combo.sat.ExtendedProblem
import combo.sat.Problem
import combo.sat.optimizers.LinearOptimizer
import combo.sat.optimizers.LinearOptimizerTest

class JacopSolverTest : SolverTest() {
    override fun solver(problem: ExtendedProblem) = JacopSolver(problem.problem)
    override fun largeSolver(problem: ExtendedProblem) = JacopSolver(problem.problem)
    override fun unsatSolver(problem: ExtendedProblem) = JacopSolver(problem.problem)
    override fun timeoutSolver(problem: ExtendedProblem) = JacopSolver(problem.problem, timeout = 1L)
}

class JacopOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config)
    override fun largeOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config, timeout = 1L)
}
