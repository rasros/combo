package combo.sat.solvers

import combo.sat.*

class LocalSearchSolverTest : SolverTest() {
    override fun solver(problem: Problem, randomSeed: Int) = LocalSearchSolver(problem).apply {
        this.randomSeed = randomSeed; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WordRandomSet())
    }

    override fun unsatSolver(problem: Problem, randomSeed: Int) = LocalSearchSolver(problem).apply {
        this.randomSeed = randomSeed; timeout = 1L; maxSteps = 1; restarts = 1
    }
}

class LocalSearchLinearOptimizerTest : LinearOptimizerTest() {

    override fun optimizer(problem: Problem, randomSeed: Int) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        this.randomSeed = randomSeed; restarts = 5; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
    }

    override fun largeOptimizer(problem: Problem, randomSeed: Int) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        this.randomSeed = randomSeed; restarts = 5; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
    }

    override fun infeasibleOptimizer(problem: Problem, randomSeed: Int) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        this.randomSeed = randomSeed; timeout = 1L; restarts = 1; maxSteps = 1
    }
}

class LocalSearchOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O, randomSeed: Int) = LocalSearchOptimizer<O>(problem).apply {
        this.randomSeed = randomSeed; restarts = 100; timeout = 5 * 1000L
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WordRandomSet())
    }
}

