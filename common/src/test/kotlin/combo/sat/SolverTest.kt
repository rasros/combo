package combo.sat

import combo.math.Rng
import combo.model.ModelTest
import combo.model.ValidationException
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class SolverTest {

    abstract fun solver(problem: Problem): Solver?
    abstract fun largeSolver(problem: Problem): Solver?
    abstract fun unsatSolver(problem: Problem): Solver?
    abstract fun timeoutSolver(problem: Problem): Solver?

    @Test
    fun smallUnsat() {
        for ((i, model) in ModelTest.smallUnsatModels.withIndex()) {
            val unsatSolver = unsatSolver(model.problem)
            if (unsatSolver != null) {
                assertFailsWith(ValidationException::class, "Model $i") {
                    unsatSolver.witnessOrThrow()
                }
            }
        }
    }

    @Test
    fun smallUnsatSequence() {
        for ((i, model) in ModelTest.smallUnsatModels.withIndex()) {
            val unsatSolver = unsatSolver(model.problem)
            if (unsatSolver != null) {
                assertEquals(0, unsatSolver.sequence().count(), "Model $i")
            }
        }
    }

    @Test
    fun smallSat() {
        for ((i, model) in ModelTest.smallModels.withIndex()) {
            val solver = solver(model.problem)
            if (solver != null) {
                assertTrue(model.problem.satisfies(solver.witnessOrThrow()), "Model $i")
            }
        }
    }

    @Test
    fun smallSatSequence() {
        for ((i, model) in ModelTest.smallModels.withIndex()) {
            val solver = solver(model.problem)
            if (solver != null) {
                assertTrue(model.problem.satisfies(solver.sequence().first()), "Model $i")
            }
        }
    }

    @Test
    fun largeSat() {
        for ((i, model) in ModelTest.largeModels.withIndex()) {
            val solver = largeSolver(model.problem)
            if (solver != null) {
                assertTrue(model.problem.satisfies(solver.witnessOrThrow()), "Model $i")
            }
        }
    }

    @Test
    fun smallSatContext() {
        for ((i, model) in ModelTest.smallModels.withIndex()) {
            val solver = solver(model.problem)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Rng(i.toLong())
                val context = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.boolean())
                        context += l.asLiteral(j)
                }
                val restricted = solver.witnessOrThrow(context.toIntArray())
                assertTrue(model.problem.satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
                assertTrue(Conjunction(context.toIntArray()).satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallSatSequenceContext() {
        for ((i, model) in ModelTest.smallModels.withIndex()) {
            val solver = solver(model.problem)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Rng(i.toLong())
                val context = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.boolean())
                        context += l.asLiteral(j)
                }
                val restricted = solver.witnessOrThrow(context.toIntArray())
                assertTrue(model.problem.satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
                assertTrue(Conjunction(context.toIntArray()).satisfies(restricted),
                        "Model $i, context ${context.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallUnsatContext() {
        fun testUnsat(context: IntArray, problem: Problem) {
            val solver = unsatSolver(problem)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    solver.witnessOrThrow(context)
                }
            }
        }
        testUnsat(intArrayOf(20, 22), ModelTest.small1.problem)
        testUnsat(intArrayOf(10, 13, 15), ModelTest.small1.problem)
        testUnsat(intArrayOf(4, 9), ModelTest.small1.problem)
        testUnsat(intArrayOf(1, 6), ModelTest.small3.problem)
        testUnsat(intArrayOf(6, 8, 10), ModelTest.small3.problem)
        testUnsat(intArrayOf(12, 15, 17, 19), ModelTest.small3.problem)
        testUnsat(intArrayOf(7, 8, 10), ModelTest.small4.problem)
    }

    @Test
    fun smallUnsatSequenceContext() {
        fun testUnsat(context: IntArray, problem: Problem) {
            val solver = unsatSolver(problem)
            if (solver != null) {
                assertEquals(0, solver.sequence(context).count())
            }
        }
        testUnsat(intArrayOf(20, 22), ModelTest.small1.problem)
        testUnsat(intArrayOf(10, 13, 15), ModelTest.small1.problem)
        testUnsat(intArrayOf(4, 9), ModelTest.small1.problem)
        testUnsat(intArrayOf(1, 6), ModelTest.small3.problem)
        testUnsat(intArrayOf(6, 8, 10), ModelTest.small3.problem)
        testUnsat(intArrayOf(12, 15, 17, 19), ModelTest.small3.problem)
        testUnsat(intArrayOf(7, 8, 10), ModelTest.small4.problem)
    }

    @Test
    fun sequenceSize() {
        val problem = Problem(arrayOf(), 4)
        val solver = solver(problem)
        if (solver != null) {
            val toSet = solver.sequence().take(200).toSet()
            assertEquals(2.0.pow(problem.nbrVariables).toInt(), toSet.size)
        }
    }

    @Test
    fun timeoutWitness() {
        val solver = timeoutSolver(ModelTest.hugeModel.problem)
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.witnessOrThrow()
            }
        }
    }

    @Test
    fun timeoutSequence() {
        val solver = timeoutSolver(ModelTest.hugeModel.problem)
        if (solver != null) {
            solver.sequence().count()
        }
    }
}
