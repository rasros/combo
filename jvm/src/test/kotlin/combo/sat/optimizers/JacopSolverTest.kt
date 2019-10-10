package combo.sat.optimizers

import combo.sat.Problem

class JacopSolverTest : OptimizerTest() {

    override fun <O : ObjectiveFunction> optimizer(problem: Problem, randomSeed: Int) = null
    override fun <O : ObjectiveFunction> infeasibleOptimizer(problem: Problem, randomSeed: Int) = null

    override fun satOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem, randomSeed)
    override fun linearOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem, randomSeed)

    override fun infeasibleSatOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem, randomSeed)
    override fun infeasibleLinearOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem, randomSeed, 1L)

    override fun timeoutSatOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem, randomSeed, 1L)
    override fun timeoutLinearOptimizer(problem: Problem, randomSeed: Int) = JacopSolver(problem, randomSeed, 1L)

    override fun largeSatOptimizer(problem: Problem, randomSeed: Int) = null
    override fun largeLinearOptimizer(problem: Problem, randomSeed: Int) = null
}

