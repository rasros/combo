package combo.sat.solvers

import combo.model.ValidationException
import combo.sat.Conjunction
import combo.sat.ExtendedProblem
import combo.sat.ExtendedProblemTest
import combo.sat.ExtendedProblemTest.Companion.largeProblems
import combo.sat.ExtendedProblemTest.Companion.smallProblems
import combo.sat.ExtendedProblemTest.Companion.smallUnsatProblems
import combo.sat.Problem
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class SolverTest {

    abstract fun solver(problem: ExtendedProblem): Solver?
    abstract fun largeSolver(problem: ExtendedProblem): Solver?
    abstract fun unsatSolver(problem: ExtendedProblem): Solver?
    abstract fun timeoutSolver(problem: ExtendedProblem): Solver?

    @Test
    fun smallUnsat() {
        for ((i, problem) in smallUnsatProblems.withIndex()) {
            val unsatSolver = unsatSolver(problem)
            if (unsatSolver != null) {
                assertFailsWith(ValidationException::class, "Model $i") {
                    unsatSolver.witnessOrThrow()
                }
            }
        }
    }

    @Test
    fun smallUnsatSequence() {
        for ((i, problem) in smallUnsatProblems.withIndex()) {
            val unsatSolver = unsatSolver(problem)
            if (unsatSolver != null) {
                assertEquals(0, unsatSolver.sequence().count(), "Model $i")
            }
        }
    }

    @Test
    fun smallSat() {
        for ((i, problem) in smallProblems.withIndex()) {
            val solver = solver(problem)
            if (solver != null) {
                assertTrue(problem.problem.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(problem.problem.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatSequence() {
        for ((i, problem) in smallProblems.withIndex()) {
            val solver = solver(problem)
            if (solver != null) {
                assertTrue(problem.problem.satisfies(solver.sequence().first()), "Model $i")
            }
        }
    }

    @Test
    fun largeSat() {
        for ((i, problem) in largeProblems.withIndex()) {
            val solver = largeSolver(problem)
            if (solver != null) {
                assertTrue(problem.problem.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(problem.problem.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatContext() {
        for ((i, problem) in smallProblems.withIndex()) {
            val solver = solver(problem)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val context = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        context += l.asLiteral(j)
                }
                assertTrue(problem.problem.satisfies(l))
                val restricted = solver.witnessOrThrow(context.toIntArray())
                assertTrue(problem.problem.satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
                assertTrue(Conjunction(context.toIntArray()).satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallSatSequenceContext() {
        for ((i, problem) in smallProblems.withIndex()) {
            val solver = solver(problem)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val context = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        context += l.asLiteral(j)
                }
                val restricted = solver.witnessOrThrow(context.toIntArray())
                assertTrue(problem.problem.satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
                assertTrue(Conjunction(context.toIntArray()).satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallUnsatContext() {
        fun testUnsat(context: IntArray, problem: ExtendedProblem) {
            val solver = unsatSolver(problem)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    val l = solver.witnessOrThrow(context)
                    println(l)
                }
            }
        }
        testUnsat(intArrayOf(20, 22), smallProblems[0])
        testUnsat(intArrayOf(10, 13, 15), smallProblems[0])
        testUnsat(intArrayOf(4, 9), smallProblems[0])
        testUnsat(intArrayOf(1, 6), smallProblems[2])
        testUnsat(intArrayOf(6, 8, 10), smallProblems[2])
        testUnsat(intArrayOf(12, 15, 17, 19), smallProblems[2])
        testUnsat(intArrayOf(7, 8, 10), smallProblems[3])
    }

    @Test
    fun smallUnsatSequenceContext() {
        fun testUnsat(context: IntArray, problem: ExtendedProblem) {
            val solver = unsatSolver(problem)
            if (solver != null) {
                assertEquals(0, solver.sequence(context).count())
            }
        }
        testUnsat(intArrayOf(20, 22), smallProblems[0])
        testUnsat(intArrayOf(10, 13, 15), smallProblems[0])
        testUnsat(intArrayOf(4, 9), smallProblems[0])
        testUnsat(intArrayOf(1, 6), smallProblems[2])
        testUnsat(intArrayOf(6, 8, 10), smallProblems[2])
        testUnsat(intArrayOf(12, 15, 17, 19), smallProblems[2])
        testUnsat(intArrayOf(7, 8, 10), smallProblems[3])
    }

    @Test
    fun sequenceSize() {
        val problem = ExtendedProblem(Problem(arrayOf(), 4))
        val solver = solver(problem)
        if (solver != null) {
            val toSet = solver.sequence().take(200).toSet()
            assertEquals(2.0.pow(4).toInt(), toSet.size)
        }
    }

    @Test
    fun timeoutWitness() {
        val solver = timeoutSolver(ExtendedProblemTest.hugeProblem)
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.witnessOrThrow()
            }
        }
    }

    @Test
    fun timeoutSequence() {
        val solver = timeoutSolver(ExtendedProblemTest.hugeProblem)
        if (solver != null) {
            solver.sequence().count()
        }
    }
}
