package combo.sat.solvers

import combo.math.Vector
import combo.sat.*
import combo.test.assertEquals
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class LinearOptimizerTest {

    abstract fun optimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig = SolverConfig()): Optimizer<LinearObjective>?
    abstract fun largeOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig = SolverConfig()): Optimizer<LinearObjective>?
    abstract fun unsatOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig = SolverConfig()): Optimizer<LinearObjective>?
    abstract fun timeoutOptimizer(problem: Problem, propTable: UnitPropagationTable, config: SolverConfig = SolverConfig()): Optimizer<LinearObjective>?

    @Test
    fun smallUnsat() {
        for ((i, d) in SolverTest.smallUnsatProblems.withIndex()) {
            val (p, pt) = d
            val unsatSolver = unsatOptimizer(p, pt)
            if (unsatSolver != null) {
                assertFailsWith(ValidationException::class, "Model $i") {
                    unsatSolver.optimizeOrThrow(LinearObjective(DoubleArray(p.nbrVariables) { 0.0 }))
                }
            }
        }
    }

    @Test
    fun smallOptimize() {
        fun testOptimize(weights: DoubleArray, d: Pair<Problem, UnitPropagationTable>, maximize: Boolean, target: Double, delta: Double = max(target * .3, 0.01)) {
            val (p, pt) = d
            val solver = optimizer(p, pt, SolverConfig(maximize = maximize, randomSeed = 0L))
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(weights))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
                val optimize = solver.optimize(LinearObjective(weights))!!
                assertTrue(p.satisfies(optimize))
                assertEquals(target, optimize dot weights, delta)
            }
        }
        testOptimize(DoubleArray(12) { 1.0 }, SolverTest.smallProblems[0], true, 10.0)
        testOptimize(DoubleArray(12) { 1.0 }, SolverTest.smallProblems[0], false, 1.0)
        testOptimize(DoubleArray(12) { 0.0 }, SolverTest.smallProblems[0], true, 0.0)
        testOptimize(DoubleArray(12) { 0.0 }, SolverTest.smallProblems[0], false, 0.0)
        testOptimize(DoubleArray(12) { it.toDouble() }, SolverTest.smallProblems[0], true, 50.0)
        testOptimize(DoubleArray(12) { it.toDouble() }, SolverTest.smallProblems[0], false, 3.0)
        testOptimize(DoubleArray(12) { it.toDouble() * .1 }, SolverTest.smallProblems[0], true, 5.0)
        testOptimize(DoubleArray(12) { it.toDouble() * .1 }, SolverTest.smallProblems[0], false, 0.3)

        testOptimize(DoubleArray(1) { 0.0 }, SolverTest.smallProblems[1], true, 0.0)
        testOptimize(DoubleArray(1) { 0.0 }, SolverTest.smallProblems[1], false, 0.0)
        testOptimize(DoubleArray(1) { 1.0 }, SolverTest.smallProblems[1], true, 1.0)
        testOptimize(DoubleArray(1) { 1.0 }, SolverTest.smallProblems[1], false, 0.0)
    }

    @Test
    fun largeOptimize() {
        fun testOptimize(weights: Vector, d: Pair<Problem, UnitPropagationTable>, maximize: Boolean, target: Double, delta: Double) {
            val (p, pt) = d
            val solver = largeOptimizer(p, pt, SolverConfig(maximize = maximize))
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(weights))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
            }
        }
        testOptimize(DoubleArray(49) { 1.0 }, SolverTest.largeProblems[0], true, 5.0, 1.0)
        testOptimize(DoubleArray(49) { 1.0 }, SolverTest.largeProblems[0], false, 1.0, 0.0)
    }

    @Test
    fun smallSatContext() {
        for ((i, d) in SolverTest.smallProblems.withIndex()) {
            val (p, pt) = d
            val solver = optimizer(p, pt)
            if (solver != null) {
                val l = solver.optimizeOrThrow(LinearObjective(DoubleArray(p.nbrVariables)))
                val rng = Random(i.toLong())
                val assumptions = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions += l.asLiteral(j)
                }
                val restricted = solver.optimizeOrThrow(LinearObjective(DoubleArray(p.nbrVariables)), assumptions.toIntArray())
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions.toIntArray()).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallUnsatContext() {
        fun testUnsat(assumptions: IntArray, d: Pair<Problem, UnitPropagationTable>) {
            val (p, pt) = d
            val solver = unsatOptimizer(p, pt)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    solver.optimizeOrThrow(LinearObjective(DoubleArray(p.nbrVariables)), assumptions)
                }
            }
        }
        testUnsat(intArrayOf(20, 22), SolverTest.smallProblems[0])
        testUnsat(intArrayOf(10, 13, 15), SolverTest.smallProblems[0])
        testUnsat(intArrayOf(4, 9), SolverTest.smallProblems[0])
        testUnsat(intArrayOf(1, 6), SolverTest.smallProblems[2])
        testUnsat(intArrayOf(6, 8, 10), SolverTest.smallProblems[2])
        testUnsat(intArrayOf(12, 15, 17, 19), SolverTest.smallProblems[2])
        testUnsat(intArrayOf(7, 8, 10), SolverTest.smallProblems[3])
    }

    @Test
    fun smallOptimizeContext() {
        fun testOptimize(assumptions: IntArray, weights: Vector, d: Pair<Problem, UnitPropagationTable>, maximize: Boolean, target: Double, delta: Double = max(target * .3, 0.01)) {
            val (p, pt) = d
            val solver = optimizer(p, pt, SolverConfig(maximize = maximize, randomSeed = 0L))
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(weights), assumptions)
                assertTrue(Conjunction(assumptions).satisfies(optimizeOrThrow))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
                val optimize = solver.optimize(LinearObjective(weights), assumptions)!!
                assertTrue(Conjunction(assumptions).satisfies(optimize))
                assertTrue(p.satisfies(optimize))
                assertEquals(target, optimize dot weights, delta)
            }
        }

        testOptimize(intArrayOf(1, 3, 5, 7), DoubleArray(12) { 1.0 }, SolverTest.smallProblems[0], true, 6.0)
        testOptimize(intArrayOf(0, 10, 20), DoubleArray(12) { 1.0 }, SolverTest.smallProblems[0], false, 6.0)
        testOptimize(intArrayOf(13, 20), DoubleArray(12) { 0.0 }, SolverTest.smallProblems[0], true, 0.0)
        testOptimize(intArrayOf(5, 8, 10), DoubleArray(12) { 0.0 }, SolverTest.smallProblems[0], false, 0.0)
        testOptimize(intArrayOf(4, 6, 13, 17), DoubleArray(12) { it.toDouble() }, SolverTest.smallProblems[0], true, 50.0)
        testOptimize(intArrayOf(16), DoubleArray(12) { it.toDouble() }, SolverTest.smallProblems[0], false, 42.0)
        testOptimize(intArrayOf(22), DoubleArray(12) { it.toDouble() * .1 }, SolverTest.smallProblems[0], true, 5.0)
        testOptimize(intArrayOf(15, 17, 18), DoubleArray(12) { it.toDouble() * .1 }, SolverTest.smallProblems[0], false, 3.3)

        testOptimize(intArrayOf(0), DoubleArray(1) { 0.0 }, SolverTest.smallProblems[1], true, 0.0)
        testOptimize(intArrayOf(0), DoubleArray(1) { 0.0 }, SolverTest.smallProblems[1], false, 0.0)
        testOptimize(intArrayOf(0), DoubleArray(1) { 1.0 }, SolverTest.smallProblems[1], true, 1.0)
        testOptimize(intArrayOf(0), DoubleArray(1) { 1.0 }, SolverTest.smallProblems[1], false, 1.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 0.0 }, SolverTest.smallProblems[1], true, 0.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 0.0 }, SolverTest.smallProblems[1], false, 0.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 1.0 }, SolverTest.smallProblems[1], true, 0.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 1.0 }, SolverTest.smallProblems[1], false, 0.0)
    }

    @Test
    fun timeoutOptimize() {
        val solver = timeoutOptimizer(SolverTest.hugeProblem.first, SolverTest.hugeProblem.second)
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.optimizeOrThrow(LinearObjective(DoubleArray(SolverTest.hugeProblem.first.nbrVariables)))
            }
        }
    }
}

