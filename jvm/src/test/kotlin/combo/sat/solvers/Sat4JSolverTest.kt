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
    override fun solver(problem: Problem, randomSeed: Int) = Sat4JSolver(problem).apply { this.randomSeed = randomSeed }
    override fun timeoutSolver(problem: Problem, randomSeed: Int) = Sat4JSolver(problem).apply {
        timeout = 1L; maxConflicts = 1; this.randomSeed = randomSeed
    }

    override fun pbSolver(problem: Problem, randomSeed: Int) = Sat4JSolver(problem).apply { this.randomSeed = randomSeed }
    override fun numericSolver(problem: Problem, randomSeed: Int) = null

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
            solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables) { Random.nextNormal() }))
        }
    }
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, randomSeed: Int) = Sat4JSolver(problem).apply { this.randomSeed = randomSeed }
    override fun timeoutOptimizer(problem: Problem, randomSeed: Int) = Sat4JSolver(problem).apply {
        timeout = 1L; maxConflicts = 1; this.randomSeed = randomSeed
    }

    override fun largeOptimizer(problem: Problem, randomSeed: Int) = null
}
