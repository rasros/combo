package combo.sat

class Sat4JSolverTest : SolverTest() {
    override fun timeoutSolver(problem: Problem) = Sat4JSolver(problem, timeout = 1L)
    override fun unsatSolver(problem: Problem) = Sat4JSolver(problem)
    override fun solver(problem: Problem) = Sat4JSolver(problem)
}

class Sat4JOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JOptimizer(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JOptimizer(problem, config)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = Sat4JOptimizer(problem, config, timeout = 1L)
}
