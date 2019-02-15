package combo.sat.solvers

import combo.math.nextNormal
import combo.sat.IterationsReachedException
import combo.sat.Problem
import org.junit.Test
import org.sat4j.minisat.SolverFactory
import org.sat4j.specs.IVec
import kotlin.random.Random
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sat4JSolverTest : SolverTest() {
    override fun solver(problem: Problem) = Sat4JSolver(problem)
    override fun timeoutSolver(problem: Problem) = null

    @Test
    fun forgetLearnedClausesTest() {
        val sat4jMemory = SolverFactory.newMiniLearningHeap()
        Sat4JSolver(SolverTest.SMALL_UNSAT_PROBLEMS[1], solverCreator = { sat4jMemory }).apply {
            this.forgetLearnedClauses = false
        }.witness()
        val learntsField = sat4jMemory::class.java.getDeclaredField("learnts").apply { isAccessible = true }
        val learnts = learntsField.get(sat4jMemory) as IVec<*>
        assertFalse(learnts.isEmpty)

        val sat4jForgetful = SolverFactory.newMiniLearningHeap()
        Sat4JSolver(SolverTest.SMALL_UNSAT_PROBLEMS[1], solverCreator = { sat4jForgetful }).apply {
            this.forgetLearnedClauses = true
        }.witness()
        val unlearnts = learntsField.get(sat4jForgetful) as IVec<*>
        assertTrue(unlearnts.isEmpty)
    }

    @Test
    fun maxConflictsTimeout() {
        val p = SMALL_UNSAT_PROBLEMS[2]
        val solver = Sat4JSolver(p).apply {
            maxConflicts = 1
        }
        assertFailsWith(IterationsReachedException::class) {
            solver.witnessOrThrow()
        }
        assertFailsWith(IterationsReachedException::class) {
            solver.sequence().count()
        }
        assertFailsWith(IterationsReachedException::class) {
            solver.optimizeOrThrow(LinearObjective(true, DoubleArray(p.nbrVariables) { Random.nextNormal() }))
        }
    }
}

class Sat4JLinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = Sat4JSolver(problem)
    override fun largeOptimizer(problem: Problem) = null
    override fun timeoutOptimizer(problem: Problem) = null
}
