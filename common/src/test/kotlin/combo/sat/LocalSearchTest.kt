package combo.sat

import combo.sat.solvers.*

class LocalSearchSolverFlipTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, timeout = 5 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, timeout = 10 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, maxSteps = 10, maxRestarts = 1)

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, timeout = 1L, maxConsideration = 1, maxSteps = Int.MAX_VALUE, maxRestarts = Int.MAX_VALUE)
}

class LocalSearchSolverPropTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, timeout = 5 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, timeout = 10 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, maxSteps = 10, maxRestarts = 1)

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, timeout = 1L, maxConsideration = 1, maxSteps = Int.MAX_VALUE, maxRestarts = Int.MAX_VALUE)
}

class LocalSearchOptimizerFlipTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, null, timeout = 5 * 1000L, restarts = 100, greedyHeuristic = false)

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = null

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, null, restarts = 1, maxSteps = 10)

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, null, timeout = 1L, restarts = Int.MAX_VALUE, maxSteps = Int.MAX_VALUE)
}

class LocalSearchOptimizerPropTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, propTable, timeout = 5 * 1000L)

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, propTable, timeout = 10 * 1000L, greedyHeuristic = true)

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, propTable, restarts = 1, maxSteps = 10)

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            LocalSearchOptimizer<LinearObjective>(problem, config, propTable, timeout = 1L, restarts = Int.MAX_VALUE, maxSteps = Int.MAX_VALUE)
}
