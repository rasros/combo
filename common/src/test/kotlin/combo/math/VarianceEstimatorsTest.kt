package combo.math

import combo.test.assertEquals
import combo.util.mapArray
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RunningVarianceTest {

    @Test
    fun fixedSamples() {
        val s = RunningVariance()
        val values = doubleArrayOf(2.1, 2.4, 3.3, 2.5, 2.2, 4.1, 3.9, 2.8)
        val mean = values.sum() / values.size
        for (value in values)
            s.accept(value)
        assertEquals(mean, s.mean, 0.1)
        val variance = values.sumByDouble { (it - mean).pow(2) } / values.size
        assertEquals(variance, s.variance, 0.2)
    }

    @Test
    fun randomSamples() {
        val r = Random(100)
        val s = generateSequence { r.nextNormal(3.0, sqrt(4.0)) }.take(200).sample(RunningVariance())
        assertEquals(3.0, s.mean, 0.2)
        assertEquals(4.0, s.variance, 0.2)
        assertEquals(2.0, s.standardDeviation, 0.2)
    }

    @Test
    fun fixedWeightedSamples() {
        val rv = RunningVariance()
        val values = doubleArrayOf(4.3, -0.4, 3.5, 2.5, 5.4, -0.1, 3.0, 2.2)
        val weights = doubleArrayOf(2.0, 2.3, 2.3, 0.9, 1.7, 1.2, 0.8, 2.0)
        for ((i, v) in values.withIndex())
            rv.accept(v, weights[i])
        val sum = values dot weights
        val mean = sum / weights.sum()
        assertEquals(rv.nbrWeightedSamples, weights.sum(), 0.1)
        assertEquals(mean, rv.mean, 0.1)
        val variance = values.mapIndexed { i, v ->
            weights[i] * (v - mean).pow(2)
        }.sum() / weights.sum()
        assertEquals(variance, rv.variance, 0.1)
    }
}

class ExponentialDecayVarianceTest {

    @Test
    fun fixedSamples() {
        val s = ExponentialDecayVariance(8)
        val values = doubleArrayOf(2.1, 2.4, 3.3, 2.5, 2.2, 4.1, 3.9, 2.8)
        val mean = values.sum() / values.size
        for (value in values)
            s.accept(value)
        assertEquals(mean, s.mean, 0.1)
        val variance = values.sumByDouble { (it - mean).pow(2) } / values.size
        assertEquals(variance, s.variance, 0.1)
    }

    @Test
    fun randomSamples() {
        val r = Random(100)
        val s = generateSequence { r.nextNormal(3.0, sqrt(4.0)) }.take(200).sample(ExponentialDecayVariance(50))
        assertEquals(3.0, s.mean, 0.1)
        assertEquals(4.0, s.variance, 0.1)
        assertEquals(2.0, s.standardDeviation, 0.1)
    }

    @Test
    fun randomWeightedSamples() {
        val r = Random(100)
        val s = ExponentialDecayVariance(500)
        generateSequence { r.nextNormal(3.0, sqrt(4.0)) }.take(200).forEach { s.accept(it, 10.0) }
        assertEquals(3.0, s.mean, 0.1)
        assertEquals(4.0, s.variance, 0.1)
        assertEquals(2.0, s.standardDeviation, 0.1)
    }

    @Test
    fun fixedWeightedSamples() {
        val values = doubleArrayOf(4.3, -0.4, 3.5, 2.5, 5.4, -0.1, 3.0, 2.2)
        val weights = doubleArrayOf(2.0, 2.3, 2.3, 0.9, 1.7, 1.2, 0.8, 2.0).mapArray { it * 20 }
        val edv = ExponentialDecayVariance(weights.sum().roundToInt())
        for ((i, v) in values.withIndex())
            edv.accept(v, weights[i])
        val sum = values dot weights
        val mean = sum / weights.sum()
        assertEquals(edv.nbrWeightedSamples, weights.sum(), 1E-6)
        assertEquals(mean, edv.mean, 0.5)
        val variance = values.mapIndexed { i, v ->
            weights[i] * (v - mean).pow(2)
        }.sum() / weights.sum()
        assertEquals(variance, edv.variance, 1.0)
    }
}

class CountDataTest {
    @Test
    fun fixedSamples() {
        val s = CountData()
        for (value in doubleArrayOf(1.0, 0.3, 0.2, 0.1))
            s.accept(value)
        assertEquals(0.4, s.mean, 1E-6)
        assertEquals(0.4 * 0.6, s.variance, 1E-6)
    }

    @Test
    fun randomSamples() {
        val r = Random(101)
        val s = generateSequence { r.nextDouble() }.take(200).sample(CountData())
        assertEquals(0.5, s.mean, 0.1)
        assertEquals(0.25, s.variance, 0.1)
    }

    @Test
    fun fixedWeightedSamples() {
        val cd = CountData()
        val values = doubleArrayOf(2.0, 0.4, 2.3, 1.5, 1.4, 0.1, 0.8, 0.2)
        val weights = doubleArrayOf(2.0, 2.3, 2.9, 2.9, 1.7, 1.2, 3.8, 2.0)
        for ((i, v) in values.withIndex())
            cd.accept(v, weights[i])
        val sum = values.sum()
        assertEquals(sum, cd.sum, 1E-6)
        assertEquals(cd.nbrWeightedSamples, weights.sum(), 1E-6)
        assertEquals(cd.mean, sum / cd.nbrWeightedSamples, 1E-6)
    }

    @Test
    fun illegalValues() {
        assertFailsWith(IllegalArgumentException::class) {
            CountData().accept(5.0, 2.0)
        }
        assertFailsWith(IllegalArgumentException::class) {
            CountData().accept(-1.0, 1.0)
        }
    }
}
