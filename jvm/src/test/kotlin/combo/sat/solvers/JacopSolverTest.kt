package combo.sat.solvers

import combo.sat.Problem
import combo.sat.UnitPropagationTable

class JacopSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) = JacopSolver(problem)
    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) = JacopSolver(problem)
    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) = JacopSolver(problem)
    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) = JacopSolver(problem, timeout = 1L)
}

class JacopOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JacopSolver(problem, config)
    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JacopSolver(problem, config)
    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JacopSolver(problem, config)
    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JacopSolver(problem, config, timeout = 1L)
}
