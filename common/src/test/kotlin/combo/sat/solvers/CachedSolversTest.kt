package combo.sat.solvers

import combo.sat.LocalSearchOptimizerTest
import combo.sat.LocalSearchSolverTest
import combo.sat.Problem

class CachedSolverTest : SolverTest {
    override fun solver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().solver(problem), pNew = 0.8)

    override fun largeSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().largeSolver(problem))

    override fun unsatSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().unsatSolver(problem))

    override fun timeoutSolver(problem: Problem) =
            CachedSolver(LocalSearchSolverTest().timeoutSolver(problem))
}

class FallbackSolverTest : SolverTest {
    override fun solver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().solver(problem))

    override fun largeSolver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().largeSolver(problem))

    override fun unsatSolver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().unsatSolver(problem))

    override fun timeoutSolver(problem: Problem) =
            FallbackSolver(LocalSearchSolverTest().timeoutSolver(problem))
}

class CachedOptimizerTest : LinearOptimizerTest {
    override fun optimizer(problem: Problem) =
            CachedOptimizer(LocalSearchOptimizerTest().optimizer(problem))

    override fun largeOptimizer(problem: Problem) =
            null
    //TODO CachedOptimizer(LocalSearchOptimizerTest().largeOptimizer(problem))

    override fun unsatOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchOptimizerTest().unsatOptimizer(problem))

    override fun timeoutOptimizer(problem: Problem) =
            CachedOptimizer(LocalSearchOptimizerTest().unsatOptimizer(problem))
}

class FallbackOptimizerTest : LinearOptimizerTest {
    override fun optimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchOptimizerTest().optimizer(problem))

    override fun largeOptimizer(problem: Problem) =
            null
    //TODO FallbackOptimizer(LocalSearchOptimizerTest().largeOptimizer(problem))

    override fun unsatOptimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchOptimizerTest().unsatOptimizer(problem))

    override fun timeoutOptimizer(problem: Problem) =
            FallbackOptimizer(LocalSearchOptimizerTest().unsatOptimizer(problem))
}
