package combo.sat.solvers

import combo.sat.LocalSearchOptimizerPropTest
import combo.sat.LocalSearchSolverPropTest
import combo.sat.Problem
import combo.sat.UnitPropagationTable

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().solver(problem, propTable))

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().largeSolver(problem, propTable))

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().unsatSolver(problem, propTable))

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().timeoutSolver(problem, propTable))
}

class FallbackSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            FallbackSolver(LocalSearchSolverPropTest().solver(problem, propTable))

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            FallbackSolver(LocalSearchSolverPropTest().largeSolver(problem, propTable))

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            FallbackSolver(LocalSearchSolverPropTest().unsatSolver(problem, propTable))

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            FallbackSolver(LocalSearchSolverPropTest().timeoutSolver(problem, propTable))
}

class CachedOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            CachedOptimizer(LocalSearchOptimizerPropTest().optimizer(problem, propTable, config))

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            CachedOptimizer(LocalSearchOptimizerPropTest().largeOptimizer(problem, propTable, config))

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            CachedOptimizer(LocalSearchOptimizerPropTest().unsatOptimizer(problem, propTable, config))

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            CachedOptimizer(LocalSearchOptimizerPropTest().unsatOptimizer(problem, propTable, config))
}

class FallbackOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            FallbackOptimizer(LocalSearchOptimizerPropTest().optimizer(problem, propTable, config))

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            FallbackOptimizer(LocalSearchOptimizerPropTest().largeOptimizer(problem, propTable, config))

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            FallbackOptimizer(LocalSearchOptimizerPropTest().unsatOptimizer(problem, propTable, config))

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            FallbackOptimizer(LocalSearchOptimizerPropTest().unsatOptimizer(problem, propTable, config))
}
