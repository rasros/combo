package combo.sat.solvers

import combo.sat.Problem

class JacopSolverTest : SolverTest() {
    override fun solver(problem: Problem, randomSeed: Int) = JacopSolver(problem).apply {
        this.randomSeed = randomSeed
    }

    override fun timeoutSolver(problem: Problem, randomSeed: Int) = JacopSolver(problem).apply {
        this.randomSeed = randomSeed
        timeout = 1L
    }

    override fun largeSolver(problem: Problem, randomSeed: Int) = null
}

class JacopLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem).apply {
        this.randomSeed = randomSeed
    }

    override fun timeoutOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem).apply {
        this.randomSeed = randomSeed
        timeout = 1L
    }

    override fun largeOptimizer(problem: Problem, randomSeed: Int) = null
}
