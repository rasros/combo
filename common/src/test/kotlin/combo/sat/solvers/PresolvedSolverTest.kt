package combo.sat.solvers

import combo.sat.Problem

class PresolvedSolverTest : SolverTest() {
    override fun solver(problem: Problem) =
            PresolvedSolver(ExhaustiveSolverTest().solver(problem).sequence().toList().toTypedArray())

    override fun largeSolver(problem: Problem): PresolvedSolver? = null

    override fun unsatSolver(problem: Problem) =
            PresolvedSolver(ExhaustiveSolverTest().unsatSolver(problem).sequence().toList().toTypedArray())
}

class PresolvedLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) =
            PresolvedSolver(ExhaustiveLinearOptimizerTest().optimizer(problem).sequence().toList().toTypedArray())

    override fun largeOptimizer(problem: Problem): PresolvedSolver? = null

    override fun infeasibleOptimizer(problem: Problem) =
            PresolvedSolver(ExhaustiveLinearOptimizerTest().infeasibleOptimizer(problem).sequence().toList().toTypedArray())
}
