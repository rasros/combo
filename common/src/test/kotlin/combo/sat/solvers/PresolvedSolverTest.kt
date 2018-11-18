package combo.sat.solvers

import combo.sat.ExtendedProblem

class PresolvedSolverTest : SolverTest() {

    override fun solver(problem: ExtendedProblem) =
            PresolvedSolver(ExhaustiveSolverTest().solver(problem).sequence().toList().toTypedArray())

    override fun largeSolver(problem: ExtendedProblem) = null

    override fun unsatSolver(problem: ExtendedProblem) =
            PresolvedSolver(ExhaustiveSolverTest().unsatSolver(problem).sequence().toList().toTypedArray())

    override fun timeoutSolver(problem: ExtendedProblem) =
            PresolvedSolver(ExhaustiveSolverTest().timeoutSolver(problem).sequence().toList().toTypedArray())
}

