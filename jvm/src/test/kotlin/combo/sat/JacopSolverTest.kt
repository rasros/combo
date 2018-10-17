package combo.sat

class JacopSolverTest : SolverTest() {
    override fun solver(problem: Problem) = JacopSolver(problem)
    override fun unsatSolver(problem: Problem) = JacopSolver(problem)
    override fun timeoutSolver(problem: Problem) = JacopSolver(problem, timeout = 1L)
}

class JacopOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config)
    override fun unsatOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config)
    override fun timeoutOptimizer(problem: Problem, config: SolverConfig): LinearOptimizer = JacopSolver(problem, config, timeout = 1L)
}
