package combo.sat.solvers

import combo.sat.Problem
import combo.sat.UnitPropagationTable

class JOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JOptimizer(problem, config)
    override fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JOptimizer(problem, config, maxItr = 10)
    override fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig) = JOptimizer(problem, config, maxItr = 1)
}
