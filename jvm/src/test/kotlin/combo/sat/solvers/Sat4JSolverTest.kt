package combo.sat.solvers

import combo.sat.Problem
import combo.sat.UnitPropagationTable

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: Problem, propTable: UnitPropagationTable) = Sat4JSolver(problem)
    override fun largeSolver(problem: Problem, propTable: UnitPropagationTable) = Sat4JSolver(problem)
    override fun unsatSolver(problem: Problem, propTable: UnitPropagationTable) = Sat4JSolver(problem)
    override fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable) = Sat4JSolver(problem, timeout = 1L)
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = Sat4JLinearOptimizer(problem, config)
    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = Sat4JLinearOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = Sat4JLinearOptimizer(problem, config)
    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = Sat4JLinearOptimizer(problem, config, timeout = 1L)
}
