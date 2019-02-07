package combo.sat.solvers

import combo.math.nextNormal
import combo.math.times
import combo.sat.*
import combo.test.assertEquals
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


abstract class OptimizerTest {
    abstract fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O): Optimizer<O>

    @Test
    fun hardObjectiveTest() {
        for ((i, p) in SolverTest.SMALL_PROBLEMS.withIndex()) {
            val rng = Random(i)
            val function = InteractionObjective(DoubleArray(p.nbrVariables) { rng.nextDouble() - 0.5 })
            val optimizer = optimizer(p, function)
            val labeling = optimizer.optimizeOrThrow(function)
            assertTrue(p.satisfies(labeling))
            val optValue = function.value(labeling)
            val bruteForceLabelingIx = (0 until 2.0.pow(p.nbrVariables).toInt()).minBy {
                val l = BitFieldLabeling(p.nbrVariables, longArrayOf(it.toLong()))
                if (p.satisfies(l)) function.value(l)
                else Double.POSITIVE_INFINITY
            }
            val bruteForceValue = function.value(
                    BitFieldLabeling(p.nbrVariables, longArrayOf(bruteForceLabelingIx!!.toLong())))
            assertEquals(bruteForceValue, optValue, 0.01 * p.nbrVariables, "Model $i")
        }
    }
}


abstract class ObjectiveFunctionTest {
    abstract fun function(nbrVariables: Int): ObjectiveFunction

    @Test
    fun penaltyIncreasing() {
        val func = function(10)
        var prevValue = Double.NEGATIVE_INFINITY
        for (i in 0..100) {
            val value = func.penalty(i)
            assertTrue(value > prevValue)
            prevValue = value
        }
    }

    @Test
    fun penaltyZero() {
        val func = function(1)
        assertEquals(0.0, func.penalty(0))
        assertTrue(func.penalty(1) > 0)
    }

    @Test
    fun valueAndImprovement() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = function(p.nbrVariables)
            val l = BitFieldLabeling(p.nbrVariables)
            val fac = try {
                PropSearchStateFactory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            val s = fac.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random)
            val v = f.value(l)
            val ix = Random.nextInt(p.nbrVariables)
            val imp = f.improvement(l, ix, s.changes(ix))
            s.flip(ix)
            val nv = f.value(l)
            assertEquals(imp, v - nv, 1E-3)
        }
    }


    @Test
    fun lowerBoundExhaustive() {
        val func = function(5)
        for (i in 0 until 32) {
            val l = BitFieldLabeling(5, longArrayOf(i.toLong()))
            assertTrue(func.value(l) >= func.lowerBound())
        }
    }
}

class InteractionObjective(weights: DoubleArray) : ObjectiveFunction {
    val weights = Array(weights.size) { i ->
        DoubleArray(weights.size) { j ->
            if (abs(i - j) <= 2) weights[i] * weights[j] else 0.0
        }
    }

    override fun value(labeling: Labeling) = (labeling.toDoubleArray() * weights).sum()
}

/**
 * This tests that the default implementations work.
 */
class InteractionObjectiveTest : ObjectiveFunctionTest() {
    override fun function(nbrVariables: Int) = InteractionObjective(DoubleArray(nbrVariables) {
        Random.nextNormal()
    })
}

class SatObjectiveTest : ObjectiveFunctionTest() {
    override fun function(nbrVariables: Int) = SatObjective
    @Test
    fun bounds() {
        assertEquals(0.0, SatObjective.lowerBound())
    }
}

class LinearObjectiveTest : ObjectiveFunctionTest() {
    override fun function(nbrVariables: Int) =
            LinearObjective(Random.nextBoolean(), DoubleArray(nbrVariables) { Random.nextNormal() })

    @Test
    fun valueOfOnes() {
        val weights = DoubleArray(8) { 1.0 }
        val max = LinearObjective(true, weights)
        val min = LinearObjective(false, weights)

        val ones = ByteArrayLabeling(ByteArray(8) { 1 })
        val zeros = ByteArrayLabeling(8)

        assertEquals(-8.0, max.value(ones), 0.0)
        assertEquals(-0.0, max.value(zeros), 0.0)
        assertEquals(8.0, min.value(ones), 0.0)
        assertEquals(0.0, min.value(zeros), 0.0)
    }

    @Test
    fun valueOfRange() {
        val weights = DoubleArray(4) { it.toDouble() }
        val min = LinearObjective(false, weights)
        val max = LinearObjective(true, weights)

        val ones = ByteArrayLabeling(ByteArray(4) { 1 })
        val zeros = ByteArrayLabeling(4)

        assertEquals(-6.0, max.value(ones), 0.0)
        assertEquals(-0.0, max.value(zeros), 0.0)
        assertEquals(6.0, min.value(ones), 0.0)
        assertEquals(0.0, min.value(zeros), 0.0)
    }
}

