package combo.sat.solvers

import combo.sat.Problem

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().solver(problem)).apply { pNew = 0.5 }

    override fun largeSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().largeSolver(problem)!!)

    override fun unsatSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().unsatSolver(problem))

    override fun timeoutSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().timeoutSolver(problem)!!)
}

class CachedOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().optimizer(problem))

    override fun largeOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().largeOptimizer(problem))

    override fun infeasibleOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().infeasibleOptimizer(problem))

    override fun timeoutOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchLinearOptimizerTest().infeasibleOptimizer(problem))
}

class FallbackSolverTest : SolverTest() {
    override fun solver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().solver(problem))

    override fun largeSolver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().largeSolver(problem)!!)

    override fun unsatSolver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().unsatSolver(problem))

    override fun timeoutSolver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().timeoutSolver(problem)!!)
}

class FallbackLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchLinearOptimizerTest().optimizer(problem))

    override fun largeOptimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchLinearOptimizerTest().largeOptimizer(problem))

    override fun infeasibleOptimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchLinearOptimizerTest().infeasibleOptimizer(problem))

    override fun timeoutOptimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchLinearOptimizerTest().infeasibleOptimizer(problem))
}
