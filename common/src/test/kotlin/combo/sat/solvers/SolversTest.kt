package combo.sat.solvers

import combo.model.TestModels.CSP_PROBLEMS
import combo.model.TestModels.LARGE_PROBLEMS
import combo.model.TestModels.NUMERIC_PROBLEMS
import combo.model.TestModels.PROBLEMS
import combo.model.TestModels.TINY_PROBLEMS
import combo.model.TestModels.UNSAT_PROBLEMS
import combo.sat.Problem
import combo.sat.UnsatisfiableException
import combo.sat.ValidationException
import combo.sat.constraints.Conjunction
import combo.sat.literal
import combo.util.IntList
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.*

abstract class SolverTest {

    abstract fun solver(problem: Problem): Solver?
    open fun cspSolver(problem: Problem): Solver? = solver(problem)
    open fun numericSolver(problem: Problem): Solver? = solver(problem)
    open fun largeSolver(problem: Problem): Solver? = solver(problem)
    open fun unsatSolver(problem: Problem): Solver? = solver(problem)
    open fun timeoutSolver(problem: Problem): Solver? = unsatSolver(problem)

    @Test
    fun emptyProblemSat() {
        val solver = solver(Problem(arrayOf(), 0))
        if (solver != null) {
            val instance = solver.witnessOrThrow()
            assertEquals(0, instance.size)
        }
    }

    @Test
    fun smallUnsat() {
        for ((i, p) in UNSAT_PROBLEMS.withIndex()) {
            assertFailsWith(ValidationException::class, "Model $i") {
                val unsatSolver = unsatSolver(p)
                if (unsatSolver != null) unsatSolver.witnessOrThrow()
                else UnsatisfiableException()
            }
        }
    }

    @Test
    fun smallUnsatSequence() {
        for ((i, p) in UNSAT_PROBLEMS.withIndex()) {
            try {
                val unsatSolver = unsatSolver(p)
                if (unsatSolver != null) {
                    assertEquals(0, unsatSolver.asSequence().count(), "Model $i")
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallSat() {
        for ((i, p) in PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun numericSat() {
        for ((i, p) in NUMERIC_PROBLEMS.withIndex()) {
            val solver = numericSolver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun cspSat() {
        for ((i, p) in CSP_PROBLEMS.withIndex()) {
            val solver = cspSolver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatSequence() {
        for ((i, p) in TINY_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.asSequence().first()), "Model $i")
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.asSequence().first()), "Model $i")
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
        for ((i, p) in PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                val instance = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until instance.size) {
                    if (rng.nextBoolean())
                        assumptions.add(instance.literal(j))
                }
                assertTrue(p.satisfies(instance))
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
        for ((i, p) in PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                val instance = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until instance.size) {
                    if (rng.nextBoolean())
                        assumptions.add(instance.literal(j))
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
        testUnsat(intArrayOf(11, 12), PROBLEMS[0])
        testUnsat(intArrayOf(6, -7, -8), PROBLEMS[0])
        testUnsat(intArrayOf(-2, 4), PROBLEMS[0])
        testUnsat(intArrayOf(1, 6), PROBLEMS[2])
        testUnsat(intArrayOf(3, 4, 5), PROBLEMS[3])
        testUnsat(intArrayOf(-10, -11, -12), PROBLEMS[3])
        testUnsat(intArrayOf(-4, 5), PROBLEMS[4])
    }

    @Test
    fun smallUnsatSequenceAssumptions() {
        fun testUnsat(assumptions: IntArray, p: Problem) {
            val solver = unsatSolver(p)
            if (solver != null) {
                assertEquals(0, solver.asSequence(assumptions).count())
            }
        }
        testUnsat(intArrayOf(11, 12), PROBLEMS[0])
        testUnsat(intArrayOf(6, -7, -8), PROBLEMS[0])
        testUnsat(intArrayOf(-2, 4), PROBLEMS[0])
        testUnsat(intArrayOf(1, 6), PROBLEMS[2])
        testUnsat(intArrayOf(3, 4, 5), PROBLEMS[3])
        testUnsat(intArrayOf(-10, -11, -12), PROBLEMS[3])
        testUnsat(intArrayOf(-4, 5), PROBLEMS[4])
    }

    @Test
    fun sequenceSize() {
        val p = Problem(arrayOf(), 4)
        val solver = solver(p)
        if (solver != null) {
            val toSet = solver.asSequence().take(200).toSet()
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
            solver.asSequence().count()
        }
    }
}
