package combo.sat.solvers

import combo.math.ImplicationDigraph
import combo.sat.FastRandomSet
import combo.sat.ImplicationConstraintCoercer
import combo.sat.Problem
import combo.sat.WeightSet

class GALinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 80
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), FastRandomSet())
    }

    override fun largeOptimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 20; restarts = 1
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
    }

    override fun infeasibleOptimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 1; maxSteps = 1
    }
}

class GAOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = GAOptimizer<O>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 50; restarts = 1
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), FastRandomSet())
    }
}

class GASolverTest : SolverTest() {
    override fun solver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L
    }

    override fun largeSolver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; candidateSize = 20
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), FastRandomSet())
    }

    override fun unsatSolver(problem: Problem) = GASolver(problem).apply {
        randomSeed = 0L; timeout = 1L; maxSteps = 1
    }
}
