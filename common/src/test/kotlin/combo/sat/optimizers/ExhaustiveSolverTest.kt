package combo.sat.optimizers

import combo.sat.Problem

class ExhaustiveSolverTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, randomSeed: Int): Optimizer<O>? {
        return ExhaustiveSolver(problem, randomSeed)
    }

    override fun <O : ObjectiveFunction> infeasibleOptimizer(problem: Problem, randomSeed: Int): Optimizer<O>? {
        return ExhaustiveSolver(problem, randomSeed, 1L)
    }
}
