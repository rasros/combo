package combo.sat.solvers

import combo.sat.Problem
import combo.sat.SolverConfig
import combo.sat.UnitPropagationTable
import kotlin.test.Ignore

@Ignore
class GAOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented")
    }

    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented")
    }

    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented")
    }

    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig): Optimizer<LinearObjective>? {
        TODO("not implemented")
    }
    /*override fun optimizer(problem: Problem, config: SolverConfig) = GALinearOptimizer(problem, config)
    override fun largeOptimizer(problem: Problem, config: SolverConfig) = GALinearOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig) = GALinearOptimizer(problem, config, maxIter = 1)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig) = GALinearOptimizer(problem, config, timeout = 1L)
    */
}

@Ignore
class GASolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable): Solver? {
        TODO("not implemented")
    }

    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable): Solver? {
        TODO("not implemented")
    }

    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable): Solver? {
        TODO("not implemented")
    }

    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable): Solver? {
        TODO("not implemented")
    }

}
