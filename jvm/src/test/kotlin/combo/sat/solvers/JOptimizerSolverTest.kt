package combo.sat.solvers

import combo.sat.Problem

class JOptimizerSolverTest : SolverTest, LinearOptimizerTest {
    override fun solver(problem: Problem) = JOptimizerSolver(problem)
    override fun largeSolver(problem: Problem): JOptimizerSolver? = null
    override fun unsatSolver(problem: Problem) = JOptimizerSolver(problem, maxIterations = 1)
    override fun optimizer(problem: Problem) = JOptimizerSolver(problem)
    override fun unsatOptimizer(problem: Problem) = JOptimizerSolver(problem, maxIterations = 1)
}

