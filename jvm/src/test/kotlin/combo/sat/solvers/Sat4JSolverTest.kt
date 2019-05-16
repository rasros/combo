package combo.sat.solvers

import combo.math.nextNormal
import combo.model.TestModels
import combo.sat.IterationsReachedException
import combo.sat.Problem
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertFailsWith

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutSolver(problem: Problem) = null
    override fun cspSolver(problem: Problem) = null
    override fun numericSolver(problem: Problem) = null

    @Test
    fun maxConflictsTimeout() {
        val p = TestModels.UNSAT_PROBLEMS[2]
        val solver = Sat4JSolver(p).apply {
            maxConflicts = 1
        }
        assertFailsWith(IterationsReachedException::class) {
            solver.witnessOrThrow()
        }
        assertFailsWith(IterationsReachedException::class) {
            solver.asSequence().count()
        }
        assertFailsWith(IterationsReachedException::class) {
            solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables) { Random.nextNormal() }))
        }
    }
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = Sat4JSolver(problem)
    override fun largeOptimizer(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutOptimizer(problem: Problem) = null
}
