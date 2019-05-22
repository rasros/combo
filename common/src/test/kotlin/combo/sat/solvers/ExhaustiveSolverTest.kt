package combo.sat.solvers

import combo.sat.Problem

class ExhaustiveSolverTest : SolverTest() {
    override fun solver(problem: Problem) = ExhaustiveSolver(problem)
    override fun largeSolver(problem: Problem): ExhaustiveSolver? = null
    override fun numericSolver(problem: Problem): ExhaustiveSolver? = null
    override fun unsatSolver(problem: Problem) = ExhaustiveSolver(problem).apply { timeout = 1L }
}

class ExhaustiveLinearOptimizerTest : LinearOptimizerTest() {
    override val isComplete = true
    override fun optimizer(problem: Problem) = ExhaustiveSolver(problem)
    override fun largeOptimizer(problem: Problem): ExhaustiveSolver? = null
    override fun infeasibleOptimizer(problem: Problem) = ExhaustiveSolver(problem).apply { timeout = 1L }
}

class ExhaustiveOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = ExhaustiveSolver(problem)
}
