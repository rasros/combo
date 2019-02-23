package combo.sat.solvers

import combo.sat.*

class LocalSearchSolverTest : SolverTest() {
    override fun solver(problem: Problem) = LocalSearchSolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; trackingInstanceFactory = BasicTrackingInstanceFactory(problem)
    }

    override fun unsatSolver(problem: Problem) = LocalSearchSolver(problem).apply {
        randomSeed = 0L; timeout = 1L; maxSteps = 1; restarts = 1; trackingInstanceFactory = BasicTrackingInstanceFactory(problem)
    }
}

class LocalSearchLinearOptimizerTest : LinearOptimizerTest() {

    override fun largeOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; restarts = 50; timeout = 5 * 1000L; trackingInstanceFactory = BasicTrackingInstanceFactory(problem); initializer = WeightInitializer()
    }

    override fun optimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; restarts = 50; timeout = 5 * 1000L; trackingInstanceFactory = BasicTrackingInstanceFactory(problem); initializer = RandomInitializer()
    }

    override fun infeasibleOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 1L; restarts = 1; maxSteps = 1; trackingInstanceFactory = BasicTrackingInstanceFactory(problem)
    }
}

class LocalSearchOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = LocalSearchOptimizer<O>(problem).apply {
        randomSeed = 0L; restarts = 500; timeout = 5 * 1000L; trackingInstanceFactory = BasicTrackingInstanceFactory(problem)
    }
}

class LocalSearchSolverPropTest : SolverTest() {
    override fun solver(problem: Problem) = LocalSearchSolver(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; trackingInstanceFactory = PropTrackingInstanceFactory(problem)
    }

    override fun unsatSolver(problem: Problem) = LocalSearchSolver(problem).apply {
        randomSeed = 0L; timeout = 1L; maxSteps = 1; restarts = 1; trackingInstanceFactory = PropTrackingInstanceFactory(problem)
    }
}

class LocalSearchLinearOptimizerPropTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 5 * 1000L; trackingInstanceFactory = PropTrackingInstanceFactory(problem); initializer = WeightInitializer()
    }

    override fun infeasibleOptimizer(problem: Problem) = LocalSearchOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0L; timeout = 1L; restarts = 1; maxSteps = 1; trackingInstanceFactory = PropTrackingInstanceFactory(problem)
    }
}

class LocalSearchOptimizerPropTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = LocalSearchOptimizer<O>(problem).apply {
        randomSeed = 0L; restarts = 100; timeout = 5 * 1000L; trackingInstanceFactory = PropTrackingInstanceFactory(problem); initializer = RandomInitializer()
    }
}
