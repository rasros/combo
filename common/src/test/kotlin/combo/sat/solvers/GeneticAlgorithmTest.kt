package combo.sat.solvers

import combo.sat.Problem
import combo.sat.RandomInitializer
import combo.sat.WeightInitializer

class GALinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; initializer = RandomInitializer(); candidateSize = 100; restarts = 2
    }

    override fun largeOptimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; initializer = WeightInitializer(0.2)
    }

    override fun infeasibleOptimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 1; maxSteps = 1
    }
}

class GAOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = GAOptimizer<O>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 100; restarts = 5
    }
}

class GASolverTest : SolverTest() {
    override fun solver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L
    }

    override fun largeSolver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 100
    }

    override fun unsatSolver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; timeout = 1L; maxSteps = 1
    }
}

