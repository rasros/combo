package combo.sat.solvers

import combo.model.ModelTest
import combo.sat.Conjunction
import combo.sat.Problem
import combo.sat.UnsatisfiableException
import combo.sat.ValidationException
import combo.util.IntList
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class SolverTest {

    abstract fun solver(problem: Problem): Solver?
    open fun largeSolver(problem: Problem): Solver? = solver(problem)
    open fun unsatSolver(problem: Problem): Solver? = solver(problem)
    open fun timeoutSolver(problem: Problem): Solver? = unsatSolver(problem)

    companion object {
        val SMALL_PROBLEMS: List<Problem> by lazy { ModelTest.SMALL_MODELS.map { m -> m.problem } }
        val SMALL_UNSAT_PROBLEMS: List<Problem> by lazy { ModelTest.SMALL_UNSAT_MODELS.map { m -> m.problem } }
        val LARGE_PROBLEMS: List<Problem>  by lazy { ModelTest.LARGE_MODEL.map { m -> m.problem } }
    }

    @Test
    fun emptyProblemSat() {
        val solver = solver(Problem(arrayOf(), 0))
        if (solver != null) {
            val l = solver.witnessOrThrow()
            assertEquals(0, l.size)
        }
    }

    @Test
    fun smallUnsat() {
        for ((i, p) in SMALL_UNSAT_PROBLEMS.withIndex()) {
            assertFailsWith(ValidationException::class, "Model $i") {
                val unsatSolver = unsatSolver(p)
                if (unsatSolver != null) unsatSolver.witnessOrThrow()
                else UnsatisfiableException()
            }
        }
    }

    @Test
    fun smallUnsatSequence() {
        for ((i, p) in SMALL_UNSAT_PROBLEMS.withIndex()) {
            try {
                val unsatSolver = unsatSolver(p)
                if (unsatSolver != null) {
                    assertEquals(0, unsatSolver.sequence().count(), "Model $i")
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallSat() {
        for ((i, p) in SMALL_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatSequence() {
        for ((i, p) in SMALL_PROBLEMS.withIndex()) {
            try {
                val solver = solver(p)
                if (solver != null) {
                    assertTrue(p.satisfies(solver.sequence().first()), "Model $i")
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallSatAfterSequence() {
        for ((i, p) in SMALL_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.sequence().first()), "Model $i")
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.sequence().first()), "Model $i")
            }
        }
    }

    @Test
    fun largeSat() {
        for ((i, p) in LARGE_PROBLEMS.withIndex()) {
            val solver = largeSolver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatAssumptionsAuto() {
        for ((i, p) in SMALL_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions.add(l.literal(j))
                }
                assertTrue(p.satisfies(l))
                val restricted = solver.witnessOrThrow(assumptions.toArray())
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallSatSequenceAssumptions() {
        for ((i, p) in SMALL_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions.add(l.literal(j))
                }
                val restricted = solver.witnessOrThrow(assumptions.toArray())
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallUnsatAssumptions() {
        fun testUnsat(assumptions: IntArray, p: Problem) {
            assertFailsWith(ValidationException::class) {
                val solver = unsatSolver(p)
                if (solver != null) solver.witnessOrThrow(assumptions)
                else throw UnsatisfiableException()
            }
        }
        testUnsat(intArrayOf(20, 22), SMALL_PROBLEMS[0])
        testUnsat(intArrayOf(10, 13, 15), SMALL_PROBLEMS[0])
        testUnsat(intArrayOf(4, 9), SMALL_PROBLEMS[0])
        testUnsat(intArrayOf(1, 6), SMALL_PROBLEMS[2])
        testUnsat(intArrayOf(6, 8, 10), SMALL_PROBLEMS[2])
        testUnsat(intArrayOf(12, 15, 17, 19), SMALL_PROBLEMS[2])
        testUnsat(intArrayOf(7, 8, 10), SMALL_PROBLEMS[3])
    }

    @Test
    fun smallUnsatSequenceAssumptions() {
        fun testUnsat(assumptions: IntArray, p: Problem) {
            val solver = unsatSolver(p)
            if (solver != null) {
                assertEquals(0, solver.sequence(assumptions).count())
            }
        }
        testUnsat(intArrayOf(20, 22), SMALL_PROBLEMS[0])
        testUnsat(intArrayOf(10, 13, 15), SMALL_PROBLEMS[0])
        testUnsat(intArrayOf(4, 9), SMALL_PROBLEMS[0])
        testUnsat(intArrayOf(1, 6), SMALL_PROBLEMS[2])
        testUnsat(intArrayOf(6, 8, 10), SMALL_PROBLEMS[2])
        testUnsat(intArrayOf(12, 15, 17, 19), SMALL_PROBLEMS[2])
        testUnsat(intArrayOf(7, 8, 10), SMALL_PROBLEMS[3])
    }

    @Test
    fun sequenceSize() {
        val p = Problem(arrayOf(), 4)
        val solver = solver(p)
        if (solver != null) {
            val toSet = solver.sequence().take(200).toSet()
            assertEquals(2.0.pow(4).toInt(), toSet.size)
        }
    }

    @Test
    fun timeoutWitness() {
        val solver = timeoutSolver(LARGE_PROBLEMS[1])
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.witnessOrThrow()
            }
        }
    }

    @Test
    fun timeoutSequence() {
        val solver = timeoutSolver(LARGE_PROBLEMS[1])
        if (solver != null) {
            solver.sequence().count()
        }
    }
}
