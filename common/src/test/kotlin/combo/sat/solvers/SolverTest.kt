package combo.sat.solvers

import combo.model.ModelTest
import combo.sat.*
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class SolverTest {

    abstract fun solver(problem: Problem, propTable: UnitPropagationTable): Solver?
    abstract fun largeSolver(problem: Problem, propTable: UnitPropagationTable): Solver?
    abstract fun unsatSolver(problem: Problem, propTable: UnitPropagationTable): Solver?
    abstract fun timeoutSolver(problem: Problem, propTable: UnitPropagationTable): Solver?

    companion object {
        val smallUnsatProblems: List<Pair<Problem, UnitPropagationTable>> = ModelTest.smallUnsatModels.mapIndexed { i, m ->
            Pair(m.problem, UnitPropagationTableTest.smallUnsatPropTables[i])
        }
        val smallProblems: List<Pair<Problem, UnitPropagationTable>> = ModelTest.smallModels.mapIndexed { i, m ->
            Pair(m.problem, UnitPropagationTableTest.smallPropTables[i])
        }
        val largeProblems: List<Pair<Problem, UnitPropagationTable>> = ModelTest.largeModels.mapIndexed { i, m ->
            Pair(m.problem, UnitPropagationTableTest.largePropTables[i])
        }
        val hugeProblem: Pair<Problem, UnitPropagationTable> = Pair(ModelTest.hugeModel.problem, UnitPropagationTableTest.hugePropTable)
    }

    @Test
    fun emptyProblemSat() {
        val p = Problem(arrayOf(), 0)
        val pt = UnitPropagationTable(p)
        val solver = solver(p, pt)
        if (solver != null) {
            val l = solver.witnessOrThrow()
            assertEquals(0, l.size)
        }
    }

    @Test
    fun smallUnsat() {
        for ((i, d) in smallUnsatProblems.withIndex()) {
            val (p, pt) = d
            val unsatSolver = unsatSolver(p, pt)
            if (unsatSolver != null) {
                assertFailsWith(ValidationException::class, "Model $i") {
                    unsatSolver.witnessOrThrow()
                }
            }
        }
    }

    @Test
    fun smallUnsatSequence() {
        for ((i, d) in smallUnsatProblems.withIndex()) {
            val (p, pt) = d
            val unsatSolver = unsatSolver(p, pt)
            if (unsatSolver != null) {
                assertEquals(0, unsatSolver.sequence().count(), "Model $i")
            }
        }
    }

    @Test
    fun smallSat() {
        for ((i, d) in smallProblems.withIndex()) {
            val (p, pt) = d
            val solver = solver(p, pt)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatSequence() {
        for ((i, d) in smallProblems.withIndex()) {
            val (p, pt) = d
            val solver = solver(p, pt)
            if (solver != null) {
                assertTrue(p.satisfies(solver.sequence().first()), "Model $i")
            }
        }
    }

    @Test
    fun largeSat() {
        for ((i, d) in largeProblems.withIndex()) {
            val (p, pt) = d
            val solver = largeSolver(p, pt)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatAssumptions() {
        for ((i, d) in smallProblems.withIndex()) {
            val (p, pt) = d
            val solver = solver(p, pt)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions += l.asLiteral(j)
                }
                assertTrue(p.satisfies(l))
                val restricted = solver.witnessOrThrow(assumptions.toIntArray())
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions.toIntArray()).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallSatSequenceAssumptions() {
        for ((i, d) in smallProblems.withIndex()) {
            val (p, pt) = d
            val solver = solver(p, pt)
            if (solver != null) {
                val l = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions += l.asLiteral(j)
                }
                val restricted = solver.witnessOrThrow(assumptions.toIntArray())
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions.toIntArray()).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallUnsatAssumptions() {
        fun testUnsat(assumptions: IntArray, d: Pair<Problem, UnitPropagationTable>) {
            val (p, pt) = d
            val solver = unsatSolver(p, pt)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    val l = solver.witnessOrThrow(assumptions)
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
    fun smallUnsatSequenceAssumptions() {
        fun testUnsat(assumptions: IntArray, d: Pair<Problem, UnitPropagationTable>) {
            val (p, pt) = d
            val solver = unsatSolver(p, pt)
            if (solver != null) {
                assertEquals(0, solver.sequence(assumptions).count())
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
        val p = Problem(arrayOf(), 4)
        val pt = UnitPropagationTable(p)
        val solver = solver(p, pt)
        if (solver != null) {
            val toSet = solver.sequence().take(200).toSet()
            assertEquals(2.0.pow(4).toInt(), toSet.size)
        }
    }

    @Test
    fun timeoutWitness() {
        val solver = timeoutSolver(hugeProblem.first, hugeProblem.second)
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.witnessOrThrow()
            }
        }
    }

    @Test
    fun timeoutSequence() {
        val solver = timeoutSolver(hugeProblem.first, hugeProblem.second)
        if (solver != null) {
            solver.sequence().count()
        }
    }
}
