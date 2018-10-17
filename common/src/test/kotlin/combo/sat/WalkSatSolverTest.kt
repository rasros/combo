package combo.sat

import combo.ga.RandomInitializer

class WalkSatTest : SolverTest() {
    override fun solver(problem: Problem) = WalkSat(problem, init = RandomInitializer())
    override fun unsatSolver(problem: Problem) = WalkSat(problem, init = RandomInitializer(), maxFlips = 10, maxRestarts = 1)
    override fun timeoutSolver(problem: Problem) = WalkSat(problem, init = RandomInitializer(), timeout = 1L, maxConsideration = 1, maxFlips = 1, maxRestarts = 100)
}
