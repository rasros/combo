package combo.sat.solvers

import combo.math.FastGAMutation
import combo.math.TournamentElimination
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
        randomSeed = 0L; timeout = 5 * 1000L; maxSteps = 1; timeout = 1
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
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 50; maxSteps = 500; restartKeeps = 0.8; stallSteps = 100
        mutationProbability = 1.0; mutation = FastGAMutation(problem.nbrVariables, beta = 5.0); elimination = TournamentElimination(5)
    }

    override fun unsatSolver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; maxSteps = 1; timeout = 1; restarts = 1

    }
}

