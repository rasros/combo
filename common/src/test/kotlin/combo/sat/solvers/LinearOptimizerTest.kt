package combo.sat.solvers

import combo.math.Vector
import combo.model.TestModels
import combo.model.TestModels.MODEL1
import combo.model.TestModels.MODEL2
import combo.model.TestModels.MODEL3
import combo.model.TestModels.MODEL5
import combo.sat.*
import combo.sat.constraints.Conjunction
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
    open fun infeasibleOptimizer(problem: Problem): Optimizer<LinearObjective>? = optimizer(problem)
    open fun timeoutOptimizer(problem: Problem): Optimizer<LinearObjective>? = infeasibleOptimizer(problem)

    @Test
    fun emptyProblemOptimize() {
        val p = Problem(arrayOf(), 0)
        val optimizer = optimizer(p)
        if (optimizer != null) {
            val l = optimizer.optimizeOrThrow(LinearObjective(true, floatArrayOf()))
            assertEquals(0, l.size)
        }
    }

    @Test
    fun smallOptimizeInfeasible() {
        for ((i, p) in TestModels.UNSAT_PROBLEMS.withIndex()) {
            try {
                val unsatOptimizer = infeasibleOptimizer(p)
                if (unsatOptimizer != null) {
                    assertFailsWith(ValidationException::class, "Model $i") {
                        unsatOptimizer.optimizeOrThrow(LinearObjective(false, FloatArray(p.nbrVariables) { 0.0f }))
                    }
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallOptimizeFeasibility() {
        for (p in TestModels.PROBLEMS) {
            optimizer(p)?.optimizeOrThrow(LinearObjective(false, FloatArray(p.nbrVariables) { 0.0f }))
        }
    }

    @Test
    fun smallOptimize() {
        fun testOptimize(weights: FloatArray, p: Problem, maximize: Boolean, target: Float, delta: Float = max(target * .3f, 0.01f)) {
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

        with(MODEL1.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 1.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 10.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { 0.0f }, this, false, 0.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() }, this, true, 62.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() }, this, false, 0.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() * .1f }, this, true, 6.2f, 0.2f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() * .1f }, this, false, 0.2f, 0.2f)
        }

        with(MODEL2.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 2.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 2.0f, 1.0f)
        }

        with(MODEL3.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 4.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 2.0f, 1.0f)
        }

        with(MODEL5.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 8.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 0.0f, 1.0f)
        }
    }

    @Test
    fun largeOptimize() {
        fun testOptimize(weights: Vector, p: Problem, maximize: Boolean, target: Float, delta: Float) {
            val solver = largeOptimizer(p)
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(LinearObjective(maximize, weights))
                assertTrue(p.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
            }
        }
        with(TestModels.LARGE_PROBLEMS[0]) {
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, true, 2944.1f, 20.0f)
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, false, 16.300001f, 10.0f)
        }
        with(TestModels.LARGE_PROBLEMS[2]) {
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, true, 11475.0f, 10.0f)
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, false, -21.0f, 2.0f)
        }
    }

    @Test
    fun smallOptimizeAssumptionsFeasible() {
        for ((i, p) in TestModels.PROBLEMS.withIndex()) {
            val solver = optimizer(p)
            if (solver != null) {
                val l = solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables)))
                val rng = Random(i.toLong())
                val assumptions = IntList()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        assumptions.add(l.literal(j))
                }
                val restricted = solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables)), assumptions.toArray())
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
            val solver = infeasibleOptimizer(p)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables)), assumptions)
                }
            }
        }
        with(TestModels.PROBLEMS[0]) {
            testUnsat(intArrayOf(-2, 5), this)
            testUnsat(intArrayOf(9, -10, -11), this)
            testUnsat(intArrayOf(6, -7, -8), this)
        }
        with(TestModels.PROBLEMS[3]) {
            testUnsat(intArrayOf(-2, 3), this)
            testUnsat(intArrayOf(3, 5), this)
            testUnsat(intArrayOf(-13, -14, -15), this)
        }
        with(TestModels.PROBLEMS[4]) {
            testUnsat(intArrayOf(6, -5), this)
        }
    }

    @Test
    fun smallOptimizeAssumptions() {
        fun testOptimize(assumptions: Literals, weights: Vector, p: Problem, maximize: Boolean, target: Float, delta: Float = max(target * .3f, 0.01f)) {
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

        with(TestModels.PROBLEMS[0]) {
            testOptimize(intArrayOf(-2, -3, -6, -7), FloatArray(nbrVariables) { 1.0f }, this, true, 3.0f, 1.0f)
            testOptimize(intArrayOf(1, 5, 10), FloatArray(nbrVariables) { 1.0f }, this, false, 6.0f, 1.0f)
            testOptimize(intArrayOf(11, 8), FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(intArrayOf(5, -8, 10), FloatArray(nbrVariables) { 0.0f }, this, false, 0.0f, 0.0f)
            testOptimize(intArrayOf(2, 3, 5), FloatArray(nbrVariables) { it.toFloat() }, this, true, 62.0f, 1.0f)
            testOptimize(intArrayOf(8), FloatArray(nbrVariables) { it.toFloat() }, this, false, 16.0f, 1.0f)
            testOptimize(intArrayOf(11), FloatArray(nbrVariables) { it.toFloat() * .1f }, this, true, 5.2f, 1.0f)
            testOptimize(intArrayOf(-7, -6), FloatArray(nbrVariables) { it.toFloat() * .1f }, this, false, 0.0f, 0.1f)
        }

        with(TestModels.PROBLEMS[2]) {
            testOptimize(intArrayOf(4), FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(intArrayOf(4), FloatArray(nbrVariables) { 1.0f }, this, false, 3.0f, 1.0f)
            testOptimize(intArrayOf(-4), FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(intArrayOf(-4), FloatArray(nbrVariables) { 1.0f }, this, false, 2.0f, 1.0f)
        }
    }

    @Test
    fun timeoutOptimize() {
        val solver = timeoutOptimizer(TestModels.LARGE_PROBLEMS[1])
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.optimizeOrThrow(LinearObjective(true, FloatArray(TestModels.LARGE_PROBLEMS[1].nbrVariables)))
            }
        }
    }
}
