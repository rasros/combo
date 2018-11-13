package combo.sat.solvers

import combo.math.Vector
import combo.math.toVector
import combo.model.ModelTest
import combo.model.ValidationException
import combo.sat.Conjunction
import combo.sat.Problem
import combo.sat.dot
import combo.test.assertEquals
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class LinearOptimizerTest {

    abstract fun optimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer?
    abstract fun largeOptimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer?
    abstract fun unsatOptimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer?
    abstract fun timeoutOptimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer?

    @Test
    fun smallUnsat() {
        for ((i, model) in ModelTest.smallUnsatModels.withIndex()) {
            val unsatSolver = unsatOptimizer(model.problem)
            if (unsatSolver != null) {
                assertFailsWith(ValidationException::class, "Model $i") {
                    unsatSolver.optimizeOrThrow(DoubleArray(model.problem.nbrVariables) { 0.0 }.toVector())
                }
            }
        }
    }

    @Test
    fun smallOptimize() {
        fun testOptimize(weights: Vector, problem: Problem, maximize: Boolean, target: Double, delta: Double = max(target * .3, 0.01)) {
            val solver = optimizer(problem, SolverConfig(maximize = maximize, randomSeed = 0L))
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(weights)
                assertTrue(problem.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
                val optimize = solver.optimize(weights)!!
                assertTrue(problem.satisfies(optimize))
                assertEquals(target, optimize dot weights, delta)
            }
        }
        testOptimize(DoubleArray(12) { 1.0 }.toVector(), ModelTest.small1.problem, true, 10.0)
        testOptimize(DoubleArray(12) { 1.0 }.toVector(), ModelTest.small1.problem, false, 1.0)
        testOptimize(DoubleArray(12) { 0.0 }.toVector(), ModelTest.small1.problem, true, 0.0)
        testOptimize(DoubleArray(12) { 0.0 }.toVector(), ModelTest.small1.problem, false, 0.0)
        testOptimize(DoubleArray(12) { it.toDouble() }.toVector(), ModelTest.small1.problem, true, 50.0)
        testOptimize(DoubleArray(12) { it.toDouble() }.toVector(), ModelTest.small1.problem, false, 3.0)
        testOptimize(DoubleArray(12) { it.toDouble() * .1 }.toVector(), ModelTest.small1.problem, true, 5.0)
        testOptimize(DoubleArray(12) { it.toDouble() * .1 }.toVector(), ModelTest.small1.problem, false, 0.3)

        testOptimize(DoubleArray(1) { 0.0 }.toVector(), ModelTest.small2.problem, true, 0.0)
        testOptimize(DoubleArray(1) { 0.0 }.toVector(), ModelTest.small2.problem, false, 0.0)
        testOptimize(DoubleArray(1) { 1.0 }.toVector(), ModelTest.small2.problem, true, 1.0)
        testOptimize(DoubleArray(1) { 1.0 }.toVector(), ModelTest.small2.problem, false, 0.0)
    }

    @Test
    fun largeOptimize() {
        fun testOptimize(weights: Vector, problem: Problem, maximize: Boolean, target: Double, delta: Double) {
            val solver = largeOptimizer(problem, SolverConfig(maximize = maximize))
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(weights)
                assertTrue(problem.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
            }
        }
        testOptimize(DoubleArray(49) { 1.0 }.toVector(), ModelTest.large1.problem, true, 5.0, 1.0)
        testOptimize(DoubleArray(49) { 1.0 }.toVector(), ModelTest.large1.problem, false, 1.0, 0.0)
    }

    @Test
    fun smallSatContext() {
        for ((i, model) in ModelTest.smallModels.withIndex()) {
            val solver = optimizer(model.problem)
            if (solver != null) {
                val l = solver.optimizeOrThrow(Vector(model.problem.nbrVariables))
                val rng = Random(i.toLong())
                val context = ArrayList<Int>()
                for (j in 0 until l.size) {
                    if (rng.nextBoolean())
                        context += l.asLiteral(j)
                }
                val restricted = solver.optimizeOrThrow(Vector(model.problem.nbrVariables), context.toIntArray())
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
            val solver = unsatOptimizer(problem)
            if (solver != null) {
                assertFailsWith(ValidationException::class) {
                    solver.optimizeOrThrow(Vector(problem.nbrVariables), context)
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
    fun smallOptimizeContext() {
        fun testOptimize(context: IntArray, weights: Vector, problem: Problem, maximize: Boolean, target: Double, delta: Double = max(target * .3, 0.01)) {
            val solver = optimizer(problem, SolverConfig(maximize = maximize, randomSeed = 0L))
            if (solver != null) {
                val optimizeOrThrow = solver.optimizeOrThrow(weights, context)
                assertTrue(problem.satisfies(optimizeOrThrow))
                assertEquals(target, optimizeOrThrow dot weights, delta)
                val optimize = solver.optimize(weights, context)!!
                assertTrue(problem.satisfies(optimize))
                assertEquals(target, optimize dot weights, delta)
            }
        }

        testOptimize(intArrayOf(1, 3, 5, 7), DoubleArray(12) { 1.0 }.toVector(), ModelTest.small1.problem, true, 6.0)
        testOptimize(intArrayOf(0, 10, 20), DoubleArray(12) { 1.0 }.toVector(), ModelTest.small1.problem, false, 6.0)
        testOptimize(intArrayOf(13, 20), DoubleArray(12) { 0.0 }.toVector(), ModelTest.small1.problem, true, 0.0)
        testOptimize(intArrayOf(5, 8, 10), DoubleArray(12) { 0.0 }.toVector(), ModelTest.small1.problem, false, 0.0)
        testOptimize(intArrayOf(4, 6, 13, 17), DoubleArray(12) { it.toDouble() }.toVector(), ModelTest.small1.problem, true, 50.0)
        testOptimize(intArrayOf(16), DoubleArray(12) { it.toDouble() }.toVector(), ModelTest.small1.problem, false, 42.0)
        testOptimize(intArrayOf(22), DoubleArray(12) { it.toDouble() * .1 }.toVector(), ModelTest.small1.problem, true, 5.0)
        testOptimize(intArrayOf(15, 17, 18), DoubleArray(12) { it.toDouble() * .1 }.toVector(), ModelTest.small1.problem, false, 3.3)

        testOptimize(intArrayOf(0), DoubleArray(1) { 0.0 }.toVector(), ModelTest.small2.problem, true, 0.0)
        testOptimize(intArrayOf(0), DoubleArray(1) { 0.0 }.toVector(), ModelTest.small2.problem, false, 0.0)
        testOptimize(intArrayOf(0), DoubleArray(1) { 1.0 }.toVector(), ModelTest.small2.problem, true, 1.0)
        testOptimize(intArrayOf(0), DoubleArray(1) { 1.0 }.toVector(), ModelTest.small2.problem, false, 1.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 0.0 }.toVector(), ModelTest.small2.problem, true, 0.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 0.0 }.toVector(), ModelTest.small2.problem, false, 0.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 1.0 }.toVector(), ModelTest.small2.problem, true, 0.0)
        testOptimize(intArrayOf(1), DoubleArray(1) { 1.0 }.toVector(), ModelTest.small2.problem, false, 0.0)
    }

    @Test
    fun timeoutOptimize() {
        val solver = timeoutOptimizer(ModelTest.hugeModel.problem)
        if (solver != null) {
            assertFailsWith(ValidationException::class) {
                solver.optimizeOrThrow(Vector(ModelTest.hugeModel.problem.nbrVariables))
            }
        }
    }
}

