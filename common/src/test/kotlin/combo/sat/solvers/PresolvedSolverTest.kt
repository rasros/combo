package combo.sat.solvers

import combo.sat.Problem

class PresolvedSolverTest : SolverTest {
    override fun solver(problem: Problem) =
            PresolvedSolver(ExhaustiveSolverTest().solver(problem).sequence().toList().toTypedArray())

    override fun largeSolver(problem: Problem): PresolvedSolver? = null

    override fun unsatSolver(problem: Problem) =
            PresolvedSolver(ExhaustiveSolverTest().unsatSolver(problem).sequence().toList().toTypedArray())

    override fun timeoutSolver(problem: Problem) =
            PresolvedSolver(ExhaustiveSolverTest().timeoutSolver(problem).sequence().toList().toTypedArray())
}
