package combo.sat.solvers

import combo.math.nextNormal
import combo.model.TestModels
import combo.sat.IterationsReachedException
import combo.sat.Problem
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutSolver(problem: Problem) = Sat4JSolver(problem).apply { timeout = 1L; maxConflicts = 1 }
    override fun pbSolver(problem: Problem) = Sat4JSolver(problem)
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
        assertEquals(0, solver.asSequence().count())
        assertFailsWith(IterationsReachedException::class) {
            solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.binarySize) { Random.nextNormal() }))
        }
    }
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override val isComplete = true
    override fun optimizer(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutOptimizer(problem: Problem) = Sat4JSolver(problem).apply { timeout = 1L; maxConflicts = 1 }
    override fun largeOptimizer(problem: Problem) = null
}
