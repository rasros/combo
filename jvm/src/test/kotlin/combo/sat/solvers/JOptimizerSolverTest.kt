package combo.sat.solvers

import combo.sat.Problem

class JOptimizerSolverTest : SolverTest() {
    override fun solver(problem: Problem) = JOptimizerSolver(problem).toSolver(problem)
    override fun largeSolver(problem: Problem): Solver? = null
    override fun unsatSolver(problem: Problem) = JOptimizerSolver(problem).apply { maxIterations = 1 }.toSolver(problem)
}

class JOptimizerLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = JOptimizerSolver(problem)
    override fun infeasibleOptimizer(problem: Problem) = JOptimizerSolver(problem).apply { maxIterations = 1 }
}
