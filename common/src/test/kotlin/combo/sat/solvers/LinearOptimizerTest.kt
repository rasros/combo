package combo.sat.solvers

import combo.math.Vector
import combo.sat.*
import combo.test.assertEquals
import combo.util.IntList
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class LinearOptimizerTest {

    abstract fun optimizer(problem: Problem): Optimizer<LinearObjective>?
    open fun largeOptimizer(problem: Problem): Optimizer<LinearObjective>? = optimizer(problem)
    open fun unsatOptimizer(problem: Problem): Optimizer<LinearObjective>? = optimizer(problem)
    open fun timeoutOptimizer(problem: Problem): Optimizer<LinearObjective>? = unsatOptimizer(problem)

    @Test
    fun emptyProblemOptimize() {
        val p = Problem(arrayOf(), 0)
        val optimizer = optimizer(p)
        if (optimizer != null) {
            val l = optimizer.optimizeOrThrow(LinearObjective(true, doubleArrayOf()))
            assertEquals(0, l.size)
        }
    }

    @Test
    fun smallOptimizeInfeasible() {
        for ((i, p) in SolverTest.SMALL_UNSAT_PROBLEMS.withIndex()) {
            try {
                val unsatOptimizer = unsatOptimizer(p)
                if (unsatOptimizer != null) {
                    assertFailsWith(ValidationException::class, "Model $i") {
                        unsatOptimizer.optimizeOrThrow(LinearObjective(false, DoubleArray(p.nbrVariables) { 0.0 }))
                    }
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallOptimizeFeasibility() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            optimizer(p)?.optimizeOrThrow(LinearObjective(false, DoubleArray(p.nbrVariables) { 0.0 }))
        }
    }

    @Test
    fun smallOptimize() {
        fun testOptimize(weights: DoubleArray, p: Problem, maximize: Boolean, target: Double, delta: Double = max(target * .3, 0.01)) {
            val solver = optimizer(p)
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(maximize, weights))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
                val optimize = solver.optimize(LinearObjective(maximize, weights))!!
                assertTrue(p.satisfies(optimize))
                assertEquals(target, optimize dot weights, delta)
            }
        }
        with(SolverTest.SMALL_PROBLEMS[0]) {
            testOptimize(DoubleArray(nbrVariables) { 1.0 }, this, true, 10.0)
            testOptimize(DoubleArray(nbrVariables) { 1.0 }, this, false, 1.0)
            testOptimize(DoubleArray(nbrVariables) { 0.0 }, this, true, 0.0)
            testOptimize(DoubleArray(nbrVariables) { 0.0 }, this, false, 0.0)
            testOptimize(DoubleArray(nbrVariables) { it.toDouble() }, this, true, 50.0)
            testOptimize(DoubleArray(nbrVariables) { it.toDouble() }, this, false, 3.0)
            testOptimize(DoubleArray(nbrVariables) { it.toDouble() * .1 }, this, true, 5.0)
            testOptimize(DoubleArray(nbrVariables) { it.toDouble() * .1 }, this, false, 0.3)
        }

        with(SolverTest.SMALL_PROBLEMS[1]) {
            testOptimize(DoubleArray(nbrVariables) { 0.0 }, this, true, 0.0)
            testOptimize(DoubleArray(nbrVariables) { 0.0 }, this, false, 0.0)
            testOptimize(DoubleArray(nbrVariables) { 1.0 }, this, true, 1.0)
            testOptimize(DoubleArray(nbrVariables) { 1.0 }, this, false, 0.0)
        }
    }

    @Test
    fun largeOptimize() {
        fun testOptimize(weights: Vector, p: Problem, maximize: Boolean, target: Double, delta: Double) {
            val solver = largeOptimizer(p)
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(maximize, weights))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
            }
        }
        with(SolverTest.LARGE_PROBLEMS[0]) {
            testOptimize(DoubleArray(nbrVariables) { -2.0 + it.toDouble() * 0.1 }, this, true, 1909.0, 10.0)
            testOptimize(DoubleArray(nbrVariables) { -2.0 + it.toDouble() * 0.1 }, this, false, -10.6, 2.0)
        }
        with(SolverTest.LARGE_PROBLEMS[2]) {
            testOptimize(DoubleArray(nbrVariables) { -2.0 + it.toDouble() * 0.1 }, this, true, 11544.0, 20.0)
            testOptimize(DoubleArray(nbrVariables) { -2.0 + it.toDouble() * 0.1 }, this, false, -21.0, 2.0)
        }
    }

    @Test
    fun smallOptimizeAssumptionsFeasible() {
        for ((i, p) in SolverTest.SMALL_PROBLEMS.withIndex()) {
            val solver = optimizer(p)
            if (solver != null) {
                val l = solver.optimizeOrThrow(LinearObjective(true, DoubleArray(p.nbrVariables)))
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions.add(l.literal(j))
                }
                val restricted = solver.optimizeOrThrow(LinearObjective(true, DoubleArray(p.nbrVariables)), assumptions.toArray())
                assertTrue(p.satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
                assertTrue(Conjunction(assumptions).satisfies(restricted),
                        "Model $i, assumptions ${assumptions.joinToString(",")}")
            }
        }
    }

    @Test
    fun smallOptimizeAssumptionsInfeasible() {
        fun testUnsat(assumptions: IntArray, p: Problem) {
            val solver = unsatOptimizer(p)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    solver.optimizeOrThrow(LinearObjective(true, DoubleArray(p.nbrVariables)), assumptions)
                }
            }
        }
        with(SolverTest.SMALL_PROBLEMS[0]) {
            testUnsat(intArrayOf(20, 22), this)
            testUnsat(intArrayOf(10, 13, 15), this)
            testUnsat(intArrayOf(4, 9), this)
        }
        with(SolverTest.SMALL_PROBLEMS[2]) {
            testUnsat(intArrayOf(1, 6), this)
            testUnsat(intArrayOf(6, 8, 10), this)
            testUnsat(intArrayOf(12, 15, 17, 19), this)
        }
        with(SolverTest.SMALL_PROBLEMS[3]) {
            testUnsat(intArrayOf(7, 8, 10), this)
        }
    }

    @Test
    fun smallOptimizeAssumptions() {
        fun testOptimize(assumptions: Literals, weights: Vector, p: Problem, maximize: Boolean, target: Double, delta: Double = max(target * .3, 0.01)) {
            val solver = optimizer(p)
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(maximize, weights), assumptions)
                assertTrue(Conjunction(IntList(assumptions)).satisfies(optimizeOrThrow))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
                val optimize = solver.optimize(LinearObjective(maximize, weights), assumptions)!!
                assertTrue(Conjunction(IntList(assumptions)).satisfies(optimize))
                assertTrue(p.satisfies(optimize))
                assertEquals(target, optimize dot weights, delta)
            }
        }

        with(SolverTest.SMALL_PROBLEMS[0]) {
            testOptimize(intArrayOf(1, 3, 5, 7), DoubleArray(nbrVariables) { 1.0 }, this, true, 6.0)
            testOptimize(intArrayOf(0, 10, 20), DoubleArray(nbrVariables) { 1.0 }, this, false, 6.0)
            testOptimize(intArrayOf(13, 20), DoubleArray(nbrVariables) { 0.0 }, this, true, 0.0)
            testOptimize(intArrayOf(5, 8, 10), DoubleArray(nbrVariables) { 0.0 }, this, false, 0.0)
            testOptimize(intArrayOf(4, 6, 13, 17), DoubleArray(nbrVariables) { it.toDouble() }, this, true, 50.0)
            testOptimize(intArrayOf(16), DoubleArray(nbrVariables) { it.toDouble() }, this, false, 42.0)
            testOptimize(intArrayOf(22), DoubleArray(nbrVariables) { it.toDouble() * .1 }, this, true, 5.0)
            testOptimize(intArrayOf(15, 17, 18), DoubleArray(nbrVariables) { it.toDouble() * .1 }, this, false, 3.3)
        }

        with(SolverTest.SMALL_PROBLEMS[1]) {
            testOptimize(intArrayOf(0), DoubleArray(nbrVariables) { 0.0 }, this, true, 0.0)
            testOptimize(intArrayOf(0), DoubleArray(nbrVariables) { 0.0 }, this, false, 0.0)
            testOptimize(intArrayOf(0), DoubleArray(nbrVariables) { 1.0 }, this, true, 1.0)
            testOptimize(intArrayOf(0), DoubleArray(nbrVariables) { 1.0 }, this, false, 1.0)
            testOptimize(intArrayOf(1), DoubleArray(nbrVariables) { 0.0 }, this, true, 0.0)
            testOptimize(intArrayOf(1), DoubleArray(nbrVariables) { 0.0 }, this, false, 0.0)
            testOptimize(intArrayOf(1), DoubleArray(nbrVariables) { 1.0 }, this, true, 0.0)
            testOptimize(intArrayOf(1), DoubleArray(nbrVariables) { 1.0 }, this, false, 0.0)
        }
    }

    @Test
    fun timeoutOptimize() {
        val solver = timeoutOptimizer(SolverTest.LARGE_PROBLEMS[1])
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.optimizeOrThrow(LinearObjective(true, DoubleArray(SolverTest.LARGE_PROBLEMS[1].nbrVariables)))
            }
        }
    }
}

