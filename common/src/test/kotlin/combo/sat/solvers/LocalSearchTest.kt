package combo.sat.solvers

import combo.math.ImplicationDigraph
import combo.sat.*

class LocalSearchSolverTest : SolverTest() {
    override fun solver(problem: Problem) = LocalSearchSolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), FastRandomSet())
    }

    override fun unsatSolver(problem: Problem) = LocalSearchSolver(problem).apply {
        randomSeed = 0L; timeout = 1L; maxSteps = 1; restarts = 1
    }
}

class LocalSearchLinearOptimizerTest : LinearOptimizerTest() {

    override fun optimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; restarts = 5; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
    }

    override fun largeOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; restarts = 5; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
    }

    override fun infeasibleOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 1L; restarts = 1; maxSteps = 1
    }
}

class LocalSearchOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = LocalSearchOptimizer<O>(problem).apply {
        randomSeed = 0L; restarts = 100; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), FastRandomSet())
    }
}

