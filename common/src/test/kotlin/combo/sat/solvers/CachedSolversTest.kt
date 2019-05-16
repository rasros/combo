package combo.sat.solvers

import combo.sat.Problem

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().solver(problem)).apply { pNew = 0.5f }

    override fun unsatSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().unsatSolver(problem))
}

class CachedLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().optimizer(problem))

    override fun largeOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().largeOptimizer(problem))

    override fun timeoutOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().infeasibleOptimizer(problem))
}

class CachedOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) =
            CachedOptimizer(LocalSearchOptimizerTest().optimizer(problem, function))
}
