package combo.sat.solvers

import combo.sat.Problem

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutSolver(problem: Problem) = Sat4JSolver(problem, timeout = 1L)
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = Sat4JSolver(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun timeoutOptimizer(problem: Problem) = Sat4JSolver(problem, timeout = 1L)
}
