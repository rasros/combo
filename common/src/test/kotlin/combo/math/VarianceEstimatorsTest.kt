package combo.math

import combo.test.assertEquals
import combo.util.mapArray
import combo.util.sumByFloat
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
        val values = floatArrayOf(2.1f, 2.4f, 3.3f, 2.5f, 2.2f, 4.1f, 3.9f, 2.8f)
        val mean = values.sum() / values.size
        for (value in values)
            s.accept(value)
        assertEquals(mean, s.mean, 0.1f)
        val variance = values.sumByFloat { (it - mean).pow(2) } / values.size
        assertEquals(variance, s.variance, 0.2f)
    }

    @Test
    fun randomSamples() {
        val r = Random(100)
        val s = generateSequence { r.nextNormal(3.0f, sqrt(4.0f)) }.take(200).sample(RunningVariance())
        assertEquals(3.0f, s.mean, 0.2f)
        assertEquals(4.0f, s.variance, 0.5f)
        assertEquals(2.0f, s.standardDeviation, 0.25f)
    }

    @Test
    fun fixedWeightedSamples() {
        val rv = RunningVariance()
        val values = floatArrayOf(4.3f, -0.4f, 3.5f, 2.5f, 5.4f, -0.1f, 3.0f, 2.2f)
        val weights = floatArrayOf(2.0f, 2.3f, 2.3f, 0.9f, 1.7f, 1.2f, 0.8f, 2.0f)
        for ((i, v) in values.withIndex())
            rv.accept(v, weights[i])
        val sum = values dot weights
        val mean = sum / weights.sum()
        assertEquals(rv.nbrWeightedSamples, weights.sum(), 0.1f)
        assertEquals(mean, rv.mean, 0.1f)
        val variance = values.mapIndexed { i, v ->
            weights[i] * (v - mean).pow(2)
        }.sum() / weights.sum()
        assertEquals(variance, rv.variance, 0.1f)
    }
}

class ExponentialDecayVarianceTest {

    @Test
    fun fixedSamples() {
        val s = ExponentialDecayVariance(8)
        val values = floatArrayOf(2.1f, 2.4f, 3.3f, 2.5f, 2.2f, 4.1f, 3.9f, 2.8f)
        val mean = values.sum() / values.size
        for (value in values)
            s.accept(value)
        assertEquals(mean, s.mean, 0.1f)
        val variance = values.sumByFloat { (it - mean).pow(2) } / values.size
        assertEquals(variance, s.variance, 0.1f)
    }

    @Test
    fun randomSamples() {
        val r = Random(100)
        val s = generateSequence { r.nextNormal(3.0f, sqrt(4.0f)) }.take(200).sample(ExponentialDecayVariance(50))
        assertEquals(3.0f, s.mean, 0.1f)
        assertEquals(4.0f, s.variance, 0.1f)
        assertEquals(2.0f, s.standardDeviation, 0.1f)
    }

    @Test
    fun randomWeightedSamples() {
        val r = Random(100)
        val s = ExponentialDecayVariance(500)
        generateSequence { r.nextNormal(3.0f, sqrt(4.0f)) }.take(200).forEach { s.accept(it, 10.0f) }
        assertEquals(3.0f, s.mean, 0.1f)
        assertEquals(4.0f, s.variance, 0.1f)
        assertEquals(2.0f, s.standardDeviation, 0.1f)
    }

    @Test
    fun fixedWeightedSamples() {
        val values = floatArrayOf(4.3f, -0.4f, 3.5f, 2.5f, 5.4f, -0.1f, 3.0f, 2.2f)
        val weights = floatArrayOf(2.0f, 2.3f, 2.3f, 0.9f, 1.7f, 1.2f, 0.8f, 2.0f).mapArray { it * 20 }
        val edv = ExponentialDecayVariance(weights.sum().roundToInt())
        for ((i, v) in values.withIndex())
            edv.accept(v, weights[i])
        val sum = values dot weights
        val mean = sum / weights.sum()
        assertEquals(edv.nbrWeightedSamples, weights.sum(), 1E-6f)
        assertEquals(mean, edv.mean, 0.5f)
        val variance = values.mapIndexed { i, v ->
            weights[i] * (v - mean).pow(2)
        }.sum() / weights.sum()
        assertEquals(variance, edv.variance, 1.0f)
    }
}

class CountDataTest {
    @Test
    fun fixedSamples() {
        val s = CountData()
        for (value in floatArrayOf(1.0f, 0.3f, 0.2f, 0.1f))
            s.accept(value)
        assertEquals(0.4f, s.mean, 1E-6f)
        assertEquals(0.4f * 0.6f, s.variance, 1E-6f)
    }

    @Test
    fun randomSamples() {
        val r = Random(101)
        val s = generateSequence { r.nextFloat() }.take(200).sample(CountData())
        assertEquals(0.5f, s.mean, 0.1f)
        assertEquals(0.25f, s.variance, 0.1f)
    }

    @Test
    fun fixedWeightedSamples() {
        val cd = CountData()
        val values = floatArrayOf(2.0f, 0.4f, 2.3f, 1.5f, 1.4f, 0.1f, 0.8f, 0.2f)
        val weights = floatArrayOf(2.0f, 2.3f, 2.9f, 2.9f, 1.7f, 1.2f, 3.8f, 2.0f)
        for ((i, v) in values.withIndex())
            cd.accept(v, weights[i])
        val sum = values.sum()
        assertEquals(sum, cd.sum, 1E-6f)
        assertEquals(cd.nbrWeightedSamples, weights.sum(), 1E-6f)
        assertEquals(cd.mean, sum / cd.nbrWeightedSamples, 1E-6f)
    }

    @Test
    fun illegalValues() {
        assertFailsWith(IllegalArgumentException::class) {
            CountData().accept(5.0f, 2.0f)
        }
        assertFailsWith(IllegalArgumentException::class) {
            CountData().accept(-1.0f, 1.0f)
        }
    }
}
