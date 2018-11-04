package combo.sat

import kotlin.test.Ignore

@Ignore
class HillClimberTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config, WalkSatTest().solver(problem))

    override fun unsatOptimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config, WalkSatTest().unsatSolver(problem), restarts = 1)

    override fun timeoutOptimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config, WalkSatTest().timeoutSolver(problem), timeout = 1L, restarts = 1)
}
