package combo.sat.solvers

import combo.sat.Problem

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem, randomSeed: Int) =
            CachedSolver(LocalSearchSolverTest().solver(problem, randomSeed)).apply { pNew = 0.5f }

    override fun unsatSolver(problem: Problem, randomSeed: Int) =
            CachedSolver(LocalSearchSolverTest().unsatSolver(problem, randomSeed))
}

class CachedLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, randomSeed: Int) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().optimizer(problem, randomSeed))

    override fun largeOptimizer(problem: Problem, randomSeed: Int) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().largeOptimizer(problem, randomSeed))

    override fun timeoutOptimizer(problem: Problem, randomSeed: Int) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().infeasibleOptimizer(problem, randomSeed))
}

class CachedOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O, randomSeed: Int) =
            CachedOptimizer(LocalSearchOptimizerTest().optimizer(problem, function, randomSeed))
}
