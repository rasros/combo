package combo.sat

import kotlin.test.Ignore

class PresolvedSolverTest : SolverTest() {

    companion object {
        val soloutionsMap = HashMap<Problem, Array<Labeling>>()

        init {
            soloutionsMap[smallSat] = ExhaustiveSolver(smallSat).sequence().toList().toTypedArray()
            soloutionsMap[smallUnsat] = ExhaustiveSolver(smallUnsat).sequence().toList().toTypedArray()
            soloutionsMap[mediumSat] = ExhaustiveSolver(mediumSat).sequence().toList().toTypedArray()
            soloutionsMap[mediumUnsat] = ExhaustiveSolver(mediumUnsat).sequence().toList().toTypedArray()
            soloutionsMap[largeSat] = ExhaustiveSolver(largeSat).sequence().toList().toTypedArray()
        }
    }

    override fun solver(problem: Problem) =
            PresolvedSolver(soloutionsMap[problem]
                    ?: ExhaustiveSolverTest().solver(problem).sequence().toList().toTypedArray())

    override fun unsatSolver(problem: Problem) =
            PresolvedSolver(soloutionsMap[problem]
                    ?: ExhaustiveSolverTest().unsatSolver(problem).sequence().toList().toTypedArray())

    override fun timeoutSolver(problem: Problem) =
            PresolvedSolver(soloutionsMap.get(problem)
                    ?: ExhaustiveSolverTest().timeoutSolver(problem).sequence().toList().toTypedArray())
}

