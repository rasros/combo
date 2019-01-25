package combo.sat.solvers

import combo.sat.*

class LocalSearchSolverTest : SolverTest() {
    override fun solver(problem: Problem) = LocalSearchSolver(
            problem, randomSeed = 0L, timeout = 5 * 1000L, stateFactory = BasicSearchStateFactory(problem))

    override fun unsatSolver(problem: Problem) = LocalSearchSolver(
            problem, randomSeed = 0L, timeout = 1L, maxSteps = 1, restarts = 1, stateFactory = BasicSearchStateFactory(problem))
}

class LocalSearchLinearOptimizerTest : LinearOptimizerTest() {

    override fun largeOptimizer(problem: Problem) = LocalSearchOptimizer(
            problem, randomSeed = 0L, restarts = 50, timeout = 5 * 1000L, stateFactory = BasicSearchStateFactory(problem), selector = WeightSelector)

    override fun optimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(
            problem, randomSeed = 0L, restarts = 50, timeout = 5 * 1000L, stateFactory = BasicSearchStateFactory(problem), selector = RandomSelector)

    override fun infeasibleOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(
            problem, randomSeed = 0L, timeout = 1L, restarts = 1, maxSteps = 1, stateFactory = BasicSearchStateFactory(problem))
}

class LocalSearchOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = LocalSearchOptimizer<O>(
            problem, randomSeed = 0L, restarts = 100, timeout = 5 * 1000L, stateFactory = BasicSearchStateFactory(problem))
}

class LocalSearchSolverPropTest : SolverTest() {
    override fun solver(problem: Problem) = LocalSearchSolver(
            problem, timeout = 5 * 1000L, stateFactory = PropSearchStateFactory(problem))

    override fun unsatSolver(problem: Problem) = LocalSearchSolver(
            problem, timeout = 1L, maxSteps = 1, restarts = 1, stateFactory = PropSearchStateFactory(problem))
}

class LocalSearchLinearOptimizerPropTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = LocalSearchOptimizer(
            problem, timeout = 5 * 1000L, stateFactory = PropSearchStateFactory(problem), selector = WeightSelector)

    override fun infeasibleOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(
            problem, timeout = 1L, restarts = 1, maxSteps = 1, stateFactory = PropSearchStateFactory(problem))
}

class LocalSearchOptimizerPropTest : OptimizerTest() {

    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = LocalSearchOptimizer<O>(
            problem, randomSeed = 0L, restarts = 10, timeout = 5 * 1000L, stateFactory = PropSearchStateFactory(problem), selector = RandomSelector)
}
