package combo.math

import combo.test.assertEquals
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RandomSequenceTest {
    @Test
    fun randomSequenceUncorrelated() {
        val rs = RandomSequence(0L)
        val s1 = rs.next().let { r -> generateSequence { r.nextDouble(0.0, 100.0) }.take(200).toList().toDoubleArray() }
        val s2 = rs.next().let { r -> generateSequence { r.nextDouble(0.0, 100.0) }.take(200).toList().toDoubleArray() }
        val v1 = RunningVariance().apply {
            for (value in s1)
                accept(value)
        }
        val v2 = RunningVariance().apply {
            for (value in s2)
                accept(value)
        }
        val r1 = (0 until 100).map { (s1[it] - v1.mean) * (s2[it] - v2.mean) }.sum()
        val r2 = sqrt(v1.squaredDeviations * v2.squaredDeviations)
        val r = r1 / r2
        assertEquals(0.0, r, 0.05)
    }
}

class SamplingTest {
    @Test
    fun fixedSeed() {
        val r1 = Random(0)
        val r2 = Random(0)
        for (i in 0..100) {
            assertEquals(r1.nextNormal(), r2.nextNormal())
        }
    }

    @Test
    fun normalStats() {
        val r = Random(120)
        val s = generateSequence { r.nextNormal(mean = 1.0, std = sqrt(2.0)) }
                .take(200)
                .sample(RunningVariance())
        assertEquals(2.0, s.variance, 0.5)
        assertEquals(1.0, s.mean, 0.2)
    }

    @Test
    fun gammaShapeLessThan1() {
        val r = Random(100)
        val shape = 0.1
        val s = generateSequence { r.nextGamma(shape) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape, s.variance, 0.2)
        assertEquals(shape, s.mean, 0.1)
    }

    @Test
    fun gammaShapeEquals1() {
        val r = Random(2934)
        val shape = 1.0
        val s = generateSequence { r.nextGamma(shape) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape, s.variance, 0.2)
        assertEquals(shape, s.mean, 0.1)
    }

    @Test
    fun gammaShapeGreaterThan1() {
        val r = Random(12934)
        val shape = 2.0
        val s = generateSequence { r.nextGamma(shape) }
                .take(200000)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape, s.variance, 0.2)
        assertEquals(shape, s.mean, 0.1)
    }

    @Test
    fun gammaShapeHuge() {
        val r = Random(1934)
        val shape = 100.0
        val s = generateSequence { r.nextGamma(shape) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape, s.variance, 10.0)
        assertEquals(shape, s.mean, 5.0)
    }

    @Test
    fun betaUniform() {
        val r = Random(1023)
        val s = generateSequence { r.nextBeta(1.0, 1.0) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals(1 / 12.0, s.variance, 0.1)
        assertEquals(0.5, s.mean, 0.1)
    }

    @Test
    fun betaSkewedDown() {
        val r = Random(12410)
        val a = 1.0
        val b = 10.0
        val s = generateSequence { r.nextBeta(a, b) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals((a * b) / ((a + b).pow(2) * (a + b + 1)), s.variance, 0.1)
        assertEquals(a / (a + b), s.mean, 0.1)
    }

    @Test
    fun betaSkewedUp() {
        val r = Random(-10)
        val a = 10.0
        val b = 1.0
        val s = generateSequence { r.nextBeta(a, b) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals((a * b) / ((a + b).pow(2) * (a + b + 1)), s.variance, 0.1)
        assertEquals(a / (a + b), s.mean, 0.1)
    }

    @Test
    fun poissonSmall() {
        val r = Random(548)
        val lambda = 0.1
        val s = generateSequence { r.nextPoisson(lambda) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 0.1)
        assertEquals(lambda, s.mean, 0.05)
    }

    @Test
    fun poissonMedium() {
        val r = Random(1546)
        val lambda = 2.0
        val s = generateSequence { r.nextPoisson(lambda) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 0.5)
        assertEquals(lambda, s.mean, 0.2)
    }

    @Test
    fun poissonHuge() {
        val r = Random(68)
        val lambda = 100.0 // will trigger normal approximation
        val s = generateSequence { r.nextPoisson(lambda) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 10.0)
        assertEquals(lambda, s.mean, 0.5)
    }

    @Test
    fun bernoulli() {
        val r = Random(568)
        val p = 0.1
        val s = generateSequence { r.nextBinomial(p) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); assertFalse(it > 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals(p * (1 - p), s.variance, 0.2)
        assertEquals(p, s.mean, 0.1)
    }

    @Test
    fun binomialHuge() {
        val r = Random(789)
        val p = 0.2
        val n = 100
        val s = generateSequence { r.nextBinomial(p, n) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(n * p * (1 - p), s.variance, 10.0)
        assertEquals(n * p, s.mean, 5.0)
    }

    @Test
    fun geometric() {
        val r = Random(978546)
        val p = 10.0
        val s = generateSequence { r.nextGeometric(p) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals((1.0 - p) / p.pow(2), s.variance, 10.0)
        assertEquals(1.0 / p, s.mean, 5.0)
    }

    @Test
    fun exponential() {
        val r = Random(100)
        val rate = 10.0
        val s = generateSequence { r.nextExponential(rate) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(1.0 / rate.pow(2), s.variance, 0.5)
        assertEquals(1.0 / rate, s.mean, 0.1)
    }
}
