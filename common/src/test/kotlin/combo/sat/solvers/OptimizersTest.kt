package combo.sat.solvers

import combo.math.nextNormal
import combo.math.times
import combo.sat.*
import combo.test.assertEquals
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


abstract class OptimizerTest {
    abstract fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O): Optimizer<O>

    private fun optimizerTest(p: Problem, function: ObjectiveFunction, i: Int) {
        val optimizer = optimizer(p, function)
        val instance = optimizer.optimizeOrThrow(function)
        assertTrue(p.satisfies(instance))
        val optValue = function.value(instance)
        val bruteForceLabelingIx = (0 until 2.0.pow(p.nbrVariables).toInt()).minBy {
            val instance = BitFieldInstance(p.nbrVariables, longArrayOf(it.toLong()))
            if (p.satisfies(instance)) function.value(instance)
            else Double.POSITIVE_INFINITY
        }
        val bruteForceValue = function.value(
                BitFieldInstance(p.nbrVariables, longArrayOf(bruteForceLabelingIx!!.toLong())))
        assertEquals(bruteForceValue, optValue, 0.01 * p.nbrVariables, "Model $i")
    }

    @Test
    fun interactiveObjective() {
        for ((i, p) in SolverTest.SMALL_PROBLEMS.withIndex()) {
            for (z in 1..100) {
            val rng = Random(i)
            val function = InteractionObjective(DoubleArray(p.nbrVariables) { rng.nextDouble() - 0.5 })
                optimizerTest(p, function, i)
            }
        }
    }

    @Test
    fun oneMaxObjective() {
        for ((i, p) in SolverTest.SMALL_PROBLEMS.withIndex()) {
            val function = OneMaxObjective()
            optimizerTest(p, function, i)
        }
    }

    @Test
    @Ignore
    fun jumpObjective() {
        for ((i, p) in SolverTest.SMALL_PROBLEMS.withIndex()) {
            val bruteForceLabelingIx = (0 until 2.0.pow(p.nbrVariables).toInt()).minBy {
                val instance = BitFieldInstance(p.nbrVariables, longArrayOf(it.toLong()))
                if (p.satisfies(instance)) OneMaxObjective().value(instance)
                else Double.POSITIVE_INFINITY
            }
            val bruteForceValue = OneMaxObjective().value(
                    BitFieldInstance(p.nbrVariables, longArrayOf(bruteForceLabelingIx!!.toLong())))
            val function = JumpObjective(-bruteForceValue.toInt())
            optimizerTest(p, function, i)
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
            val instance = BitFieldInstance(p.nbrVariables)
            val fac = try {
                PropSearchStateFactory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            val s = fac.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random)
            val v = f.value(instance)
            val ix = Random.nextInt(p.nbrVariables)
            val imp = f.improvement(instance, ix, s.literalPropagations(ix))
            s.flip(ix)
            val nv = f.value(instance)
            assertEquals(imp, v - nv, 1E-3)
        }
    }


    @Test
    fun lowerBoundExhaustive() {
        val func = function(5)
        for (i in 0 until 32) {
            val instance = BitFieldInstance(5, longArrayOf(i.toLong()))
            assertTrue(func.value(instance) >= func.lowerBound())
        }
    }
}

/**
 * Simplest test function, most likely unimodal
 */
class OneMaxObjective : ObjectiveFunction {
    override fun value(instance: Instance) = -instance.truthIterator().asSequence().count().toDouble()
}

/**
 * JUMP is a slightly harder bimodal test function than OneMax
 */
class JumpObjective(val n: Int, val m: Int = (0.8 * n).toInt()) : ObjectiveFunction {
    override fun value(instance: Instance): Double {
        val count = instance.truthIterator().asSequence().count()
        val jump = (if (count <= n - m || count == n) m + count
        else n - count).toDouble()
        return -jump
    }
}

class InteractionObjective(weights: DoubleArray) : ObjectiveFunction {
    val weights = Array(weights.size) { i ->
        DoubleArray(weights.size) { j ->
            if (abs(i - j) <= 2) weights[i] * weights[j] else 0.0
        }
    }

    override fun value(instance: Instance) = (instance.toDoubleArray() * weights).sum()
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

        val ones = ByteArrayInstance(ByteArray(8) { 1 })
        val zeros = ByteArrayInstance(8)

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

        val ones = ByteArrayInstance(ByteArray(4) { 1 })
        val zeros = ByteArrayInstance(4)

        assertEquals(-6.0, max.value(ones), 0.0)
        assertEquals(-0.0, max.value(zeros), 0.0)
        assertEquals(6.0, min.value(ones), 0.0)
        assertEquals(0.0, min.value(zeros), 0.0)
    }
}

