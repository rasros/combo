package combo.sat.solvers

import combo.sat.LocalSearchOptimizerPropTest
import combo.sat.LocalSearchSolverPropTest
import combo.sat.Problem
import combo.sat.UnitPropagationTable

class CachedSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().solver(problem, propTable), pNew = 0.5)

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().largeSolver(problem, propTable), pNew = 0.5)

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().unsatSolver(problem, propTable), pNew = 0.5)

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) =
            CachedSolver(LocalSearchSolverPropTest().timeoutSolver(problem, propTable), pNew = 0.5)
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
    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) =
            CachedOptimizer(LocalSearchOptimizerPropTest().optimizer(problem, propTable, config))

}
