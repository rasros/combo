package combo.sat.solvers

import combo.model.FloatVar
import combo.model.IntVar
import combo.model.TestModels.LARGE_SAT_PROBLEMS
import combo.model.TestModels.NUMERIC3
import combo.model.TestModels.NUMERIC_PROBLEMS
import combo.model.TestModels.PB_PROBLEMS
import combo.model.TestModels.SAT_PROBLEMS
import combo.model.TestModels.TINY_PROBLEMS
import combo.model.TestModels.UNSAT_PROBLEMS
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.test.assertContentEquals
import combo.util.IntCollection
import combo.util.IntList
import combo.util.collectionOf
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.*

abstract class SolverTest {

    abstract fun solver(problem: Problem): Solver?
    open fun pbSolver(problem: Problem): Solver? = solver(problem)
    open fun numericSolver(problem: Problem): Solver? = solver(problem)
    open fun largeSolver(problem: Problem): Solver? = solver(problem)
    open fun unsatSolver(problem: Problem): Solver? = solver(problem)
    open fun timeoutSolver(problem: Problem): Solver? = unsatSolver(problem)

    @Test
    fun emptyProblemSat() {
        val solver = solver(Problem(0, arrayOf()))
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
        for ((i, p) in SAT_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun guessReuse() {
        for (p in SAT_PROBLEMS) {
            val solver = solver(p)
            if (solver != null) {
                val initial = solver.witnessOrThrow() as MutableInstance
                assertEquals(initial, solver.witness(guess = initial))
            }
        }
    }

    @Test
    fun smallSatRepeated() {
        for (p in TINY_PROBLEMS) {
            val solver = solver(p)
            if (solver != null) {
                repeat(20) {
                    solver.witnessOrThrow()
                }
            }
        }
    }

    @Test
    fun deterministicSequence() {
        for (p in SAT_PROBLEMS) {
            val solver = solver(p)
            if (solver != null) {
                val solver1 = solver(p)!!
                solver1.randomSeed = 1
                val solver2 = solver(p)!!
                solver2.randomSeed = 1
                val solutions1 = solver1.asSequence().take(10).toList()
                val solutions2 = solver2.asSequence().take(10).toList()
                assertContentEquals(solutions1, solutions2)
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
    fun numericSatAssumptions() {
        val m = NUMERIC3
        val solver = numericSolver(m.problem)
        if (solver != null) {
            val opt1 = m.index["opt1"] as IntVar

            // First number is optional -100..100
            val intSet = solver.witnessOrThrow(collectionOf(m.index.indexOf(opt1).toLiteral(true)))
            assertTrue(NUMERIC3.toAssignment(intSet)[opt1]!! in -100..100)

            val intUnset = solver.witnessOrThrow(collectionOf(m.index.indexOf(opt1).toLiteral(false)))
            assertNull(NUMERIC3.toAssignment(intUnset)[opt1])

            // First number is optional -100..100
            val opt2 = m.index["opt2"] as FloatVar
            val floatSet = solver.witnessOrThrow(collectionOf(m.index.indexOf(opt2).toLiteral(true)))
            assertTrue(NUMERIC3.toAssignment(floatSet)[opt2]!! in -0.1f..1.0f)

            val floatUnset = solver.witnessOrThrow(collectionOf(m.index.indexOf(opt2).toLiteral(false)))
            assertNull(NUMERIC3.toAssignment(floatUnset)[opt2])
        }
    }

    @Test
    fun pbSat() {
        for ((i, p) in PB_PROBLEMS.withIndex()) {
            val solver = pbSolver(p)
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
        for ((i, p) in LARGE_SAT_PROBLEMS.withIndex()) {
            val solver = largeSolver(p)
            if (solver != null) {
                assertTrue(p.satisfies(solver.witnessOrThrow()), "Model $i")
                assertTrue(p.satisfies(solver.witness()!!), "Model $i")
            }
        }
    }

    @Test
    fun smallSatAssumptionsAuto() {
        for ((i, p) in SAT_PROBLEMS.withIndex()) {
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
                val restricted = solver.witnessOrThrow(assumptions)
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallSatSequenceAssumptions() {
        for ((i, p) in SAT_PROBLEMS.withIndex()) {
            val solver = solver(p)
            if (solver != null) {
                val instance = solver.witnessOrThrow()
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until instance.size) {
                    if (rng.nextBoolean())
                        assumptions.add(instance.literal(j))
                }
                val restricted = solver.witnessOrThrow(assumptions)
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallUnsatAssumptions() {
        fun testUnsat(assumptions: IntCollection, p: Problem) {
            assertFailsWith(ValidationException::class) {
                val solver = unsatSolver(p)
                if (solver != null) solver.witnessOrThrow(assumptions)
                else throw UnsatisfiableException()
            }
        }
        testUnsat(collectionOf(11, 12), SAT_PROBLEMS[0])
        testUnsat(collectionOf(6, -7, -8), SAT_PROBLEMS[0])
        testUnsat(collectionOf(-2, 4), SAT_PROBLEMS[0])
        testUnsat(collectionOf(1, 6), SAT_PROBLEMS[2])
        testUnsat(collectionOf(3, 4, 5), SAT_PROBLEMS[3])
        testUnsat(collectionOf(-10, -11, -12), SAT_PROBLEMS[3])
        testUnsat(collectionOf(-4, 5), SAT_PROBLEMS[4])
    }

    @Test
    fun smallUnsatSequenceAssumptions() {
        fun testUnsat(assumptions: IntCollection, p: Problem) {
            val solver = unsatSolver(p)
            if (solver != null) {
                assertEquals(0, solver.asSequence(assumptions).count())
            }
        }
        testUnsat(collectionOf(11, 12), SAT_PROBLEMS[0])
        testUnsat(collectionOf(6, -7, -8), SAT_PROBLEMS[0])
        testUnsat(collectionOf(-2, 4), SAT_PROBLEMS[0])
        testUnsat(collectionOf(1, 6), SAT_PROBLEMS[2])
        testUnsat(collectionOf(3, 4, 5), SAT_PROBLEMS[3])
        testUnsat(collectionOf(-10, -11, -12), SAT_PROBLEMS[3])
        testUnsat(collectionOf(-4, 5), SAT_PROBLEMS[4])
    }

    @Test
    fun sequenceSize() {
        val p = Problem(4, arrayOf())
        val solver = solver(p)
        if (solver != null) {
            val toSet = solver.asSequence().take(200).toSet()
            assertEquals(2.0.pow(4).toInt(), toSet.size)
        }
    }

    @Test
    fun timeoutWitness() {
        val solver = timeoutSolver(LARGE_SAT_PROBLEMS[1])
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.witnessOrThrow()
            }
        }
    }

    @Test
    fun timeoutSequence() {
        val solver = timeoutSolver(LARGE_SAT_PROBLEMS[1])
        if (solver != null) {
            solver.asSequence().count()
        }
    }
}
