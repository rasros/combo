package combo.sat

class HillClimberTest : LinearOptimizerTest() {

    override fun optimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config.copy(randomSeed = 0L),
            WalkSat(problem, config.copy(randomSeed = 0L)))

    override fun largeOptimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config.copy(randomSeed = 0L),
            WalkSat(problem, config.copy(randomSeed = 0L)))

    override fun unsatOptimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config, WalkSat(problem, maxRestarts = 1), restarts = 1)

    override fun timeoutOptimizer(problem: Problem, config: SolverConfig) = HillClimber(
            problem, config, WalkSat(problem, maxRestarts = 1, timeout = 1L),
            timeout = 1L, restarts = 1, maxSteps = 10)
}
