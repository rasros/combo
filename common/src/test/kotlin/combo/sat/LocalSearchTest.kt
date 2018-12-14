package combo.sat

import combo.sat.solvers.*

class LocalSearchTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, timeout = 5 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, maxSteps = 1000, timeout = 5 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, maxSteps = 10, maxRestarts = 1)

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), null, timeout = 1L, maxConsideration = 1, maxSteps = 1, maxRestarts = 1)
}

class LocalSearchPropTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, timeout = 5 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, maxSteps = 1000, timeout = 5 * 1000L, maxRestarts = Int.MAX_VALUE)

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, maxSteps = 10, maxRestarts = 1)

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            LocalSearchSolver(problem, SolverConfig(), propTable, timeout = 1L, maxConsideration = 1, maxSteps = 1, maxRestarts = 1)
}

class LocalSearchOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class LocalSearchOptimizerPropTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
