package combo.ga

import combo.sat.LinearOptimizer
import combo.sat.LinearOptimizerTest
import combo.sat.Problem
import combo.sat.SolverConfig

class GALinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig) = GALinearOptimizer(problem, config)

    override fun unsatOptimizer(problem: Problem, config: SolverConfig) = GALinearOptimizer(problem, config, maxIter = 1)

    override fun timeoutOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer {
        TODO("not implemented")
    }

}
