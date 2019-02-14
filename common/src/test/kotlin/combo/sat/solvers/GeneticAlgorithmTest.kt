package combo.sat.solvers

import combo.sat.Problem
import kotlin.test.Ignore

class GALinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        //stateFactory = PropSearchStateFactory(problem)
        restarts = 50
    }

    override fun infeasibleOptimizer(problem: Problem) = GAOptimizer<LinearObjective>(problem).apply {
        maxSteps = 1
        timeout = 1
    }
}

class GAOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = GAOptimizer<O>(problem).apply {
        restarts = 50
        mutationProbability = 1.0
    }
}

@Ignore
class GASolverTest : SolverTest() {
    override fun solver(problem: Problem): Solver? {
        TODO("not implemented")
    }

    override fun largeSolver(problem: Problem): Solver? {
        TODO("not implemented")
    }

    override fun unsatSolver(problem: Problem): Solver? {
        TODO("not implemented")
    }

    override fun timeoutSolver(problem: Problem): Solver? {
        TODO("not implemented")
    }
}

