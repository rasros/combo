package combo.sat.solvers

import combo.sat.Problem

class ExhaustiveSolverTest : SolverTest() {
    override fun solver(problem: Problem, randomSeed: Int) = ExhaustiveSolver(problem).apply {
        this.randomSeed = randomSeed
    }

    override fun unsatSolver(problem: Problem, randomSeed: Int) = ExhaustiveSolver(problem).apply {
        this.randomSeed = randomSeed
        timeout = 1L
    }

    override fun largeSolver(problem: Problem, randomSeed: Int): ExhaustiveSolver? = null
    override fun numericSolver(problem: Problem, randomSeed: Int): ExhaustiveSolver? = null
}

class ExhaustiveLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, randomSeed: Int) = ExhaustiveSolver(problem).apply {
        this.randomSeed = randomSeed
    }

    override fun infeasibleOptimizer(problem: Problem, randomSeed: Int) = ExhaustiveSolver(problem).apply {
        timeout = 1L
        this.randomSeed = randomSeed
    }

    override fun largeOptimizer(problem: Problem, randomSeed: Int): ExhaustiveSolver? = null
}

class ExhaustiveOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O, randomSeed: Int) = ExhaustiveSolver(problem).apply {
        this.randomSeed = randomSeed
    }
}
