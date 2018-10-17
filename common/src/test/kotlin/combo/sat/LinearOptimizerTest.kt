package combo.sat

import combo.math.Vector
import combo.math.toVector
import combo.model.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class LinearOptimizerTest {

    abstract fun optimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer
    abstract fun unsatOptimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer
    abstract fun timeoutOptimizer(problem: Problem, config: SolverConfig = SolverConfig()): LinearOptimizer


    private fun unsatOptimize(problem: Problem) {
        assertFailsWith(ValidationException::class) {
            unsatOptimizer(problem).optimizeOrThrow(DoubleArray(problem.nbrVariables).toVector())
        }
    }

    @Test
    fun smallUnsatOptimize() = unsatOptimize(smallUnsat)

    @Test
    fun mediumUnsatOptimize() = unsatOptimize(mediumUnsat)


    private fun maximize(problem: Problem, weights: Vector, expected: Double) {
        val l = optimizer(problem, SolverConfig(maximize = true)).optimizeOrThrow(weights)
        assertEquals(expected, l dot weights)
        assertTrue(problem.satisfies(l))
    }

    @Test
    fun smallSatMaximize() = maximize(smallSat, doubleArrayOf(0.7, -0.5, 0.2, 0.3).toVector(), 0.2)

    @Test
    fun mediumSatMaximize() = maximize(mediumSat, DoubleArray(mediumSat.nbrVariables) { 1.0 }.toVector(), 2.0)

    private fun minimize(problem: Problem, weights: Vector, expected: Double) {
        val l = optimizer(problem, SolverConfig(maximize = false)).optimizeOrThrow(weights)
        assertEquals(expected, l dot weights)
        assertTrue(problem.satisfies(l))
    }

    @Test
    fun smallSatMinimize() = minimize(smallSat, doubleArrayOf(-0.5, -1.0, 0.0, 0.2).toVector(), -1.0)

    @Test
    fun mediumSatMinimize() = minimize(mediumSat, DoubleArray(mediumSat.nbrVariables) { 1.0 }.toVector(), 1.0)

    private fun optimizeContextLiterals(problem: Problem, weights: Vector, expected: Double, contextLiterals: Literals) {
        val l = optimizer(problem).optimizeOrThrow(weights, contextLiterals)
        assertEquals(expected, l dot weights)
        assertTrue(problem.satisfies(l))
    }

    @Test
    fun smallSatOptimizeContextLiterals() = optimizeContextLiterals(
            smallSat, doubleArrayOf(0.0, 1.0, 0.5, 0.0).toVector(), 0.5, intArrayOf(4))

    @Test
    fun mediumSatOptimizeContextLiterals() = optimizeContextLiterals(
            mediumSat, DoubleArray(mediumSat.nbrVariables) { 1.0 }.toVector(), 2.0, intArrayOf(0))

    private fun optimizeContextLiteralsToUnsat(problem: Problem, contextLiterals: Literals) {
        assertFailsWith(ValidationException::class) {
            unsatOptimizer(problem).optimizeOrThrow(DoubleArray(problem.nbrVariables) { 1.0 }.toVector(), contextLiterals)
        }
    }

    @Test
    fun smallSatOptimizeContextLiteralsToUnsat() = optimizeContextLiteralsToUnsat(smallSat, intArrayOf(4, 6))

    @Test
    fun mediumSatOptimizeContextLiteralsToUnsat() = optimizeContextLiteralsToUnsat(mediumSat, intArrayOf(4, 6))

    @Test
    fun largeSatOptimizeContextLiteralsToUnsat() = optimizeContextLiteralsToUnsat(largeSat, intArrayOf(0, 8))

    @Test
    fun timeoutOptimize() {
        assertFailsWith(ValidationException::class) {
            timeoutOptimizer(hugeSat).optimizeOrThrow(DoubleArray(hugeSat.nbrVariables).toVector())
        }
    }
}

