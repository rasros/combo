package combo.math

import combo.test.assertEquals
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SamplingTest {
    @Test
    fun fixedSeed() {
        val r1 = ExtendedRandom(Random(0))
        val r2 = ExtendedRandom(Random(0))
        for (i in 0..100) {
            assertEquals(r1.nextGaussian(), r2.nextGaussian())
        }
    }

    @Test
    fun randomSequenceUncorrelated() {
        val rs = RandomSequence(0L)
        val s1 = rs.next().let { r -> generateSequence { r.nextDouble(0.0, 100.0) }.take(200).toList().toDoubleArray() }
        val s2 = rs.next().let { r -> generateSequence { r.nextDouble(0.0, 100.0) }.take(200).toList().toDoubleArray() }
        val v1 = RunningVariance().apply { acceptAll(s1) }
        val v2 = RunningVariance().apply { acceptAll(s2) }
        val r1 = (0 until 100).map { (s1[it] - v1.mean) * (s2[it] - v2.mean) }.sum()
        val r2 = sqrt(v1.squaredDeviations * v2.squaredDeviations)
        val r = r1 / r2
        assertEquals(0.0, r, 0.05)
    }

    @Test
    fun gaussianStats() {
        val r = ExtendedRandom(Random(120))
        val s = generateSequence { r.nextGaussian(mean = 1.0, std = sqrt(2.0)) }.take(1000).sample(RunningVariance())
        assertEquals(2.0, s.variance, 0.2)
        assertEquals(1.0, s.mean, 0.2)
    }

    @Test
    fun gammaShapeLessThan1() {
        val r = ExtendedRandom(Random(100))
        val shape = 0.1
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 0.2)
        assertEquals(shape * scale, s.mean, 0.1)
    }

    @Test
    fun gammaShapeEquals1() {
        val r = ExtendedRandom(Random(12934))
        val shape = 1.0
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 0.2)
        assertEquals(shape * scale, s.mean, 0.1)
    }

    @Test
    fun gammaShapeGreaterThan1() {
        val r = ExtendedRandom(Random(12934))
        val shape = 10.0
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 2.0)
        assertEquals(shape * scale, s.mean, 1.0)
    }

    @Test
    fun gammaShapeHuge() {
        val r = ExtendedRandom(Random(12934))
        val shape = 100.0 // will trigger gaussian approximation
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 20.0)
        assertEquals(shape * scale, s.mean, 10.0)
    }

    @Test
    fun betaUniform() {
        val r = ExtendedRandom(Random(1023))
        val s = generateSequence { r.beta(1.0, 1.0) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals(1 / 12.0, s.variance, 0.1)
        assertEquals(0.5, s.mean, 0.1)
    }

    @Test
    fun betaSkewedDown() {
        val r = ExtendedRandom(Random(12410))
        val a = 1.0
        val b = 10.0
        val s = generateSequence { r.beta(a, b) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals((a * b) / ((a + b).pow(2) * (a + b + 1)), s.variance, 0.1)
        assertEquals(a / (a + b), s.mean, 0.1)
    }

    @Test
    fun betaSkewedUp() {
        val r = ExtendedRandom(Random(-10))
        val a = 10.0
        val b = 1.0
        val s = generateSequence { r.beta(a, b) }
                .take(200)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals((a * b) / ((a + b).pow(2) * (a + b + 1)), s.variance, 0.1)
        assertEquals(a / (a + b), s.mean, 0.1)
    }

    @Test
    fun poissonSmall() {
        val r = ExtendedRandom(Random(5468))
        val lambda = 0.1
        val s = generateSequence { r.poisson(lambda) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 0.1)
        assertEquals(lambda, s.mean, 0.05)
    }

    @Test
    fun poissonMedium() {
        val r = ExtendedRandom(Random(1546))
        val lambda = 2.0
        val s = generateSequence { r.poisson(lambda) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 1.0)
        assertEquals(lambda, s.mean, 0.5)
    }

    @Test
    fun poissonHuge() {
        val r = ExtendedRandom(Random(68))
        val lambda = 100.0 // will trigger gaussian approximation
        val s = generateSequence { r.poisson(lambda) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 20.0)
        assertEquals(lambda, s.mean, 5.0)
    }

    @Test
    fun bernoulli() {
        val r = ExtendedRandom(Random(568))
        val p = 0.1
        val s = generateSequence { r.binomial(p) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); assertFalse(it > 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals(p * (1 - p), s.variance, 0.2)
        assertEquals(p, s.mean, 0.1)
    }

    @Test
    fun binomialHuge() {
        val r = ExtendedRandom(Random(789))
        val p = 0.2
        val n = 100
        val s = generateSequence { r.binomial(p, n) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(n * p * (1 - p), s.variance, 10.0)
        assertEquals(n * p, s.mean, 5.0)
    }

    @Test
    fun geometric() {
        val r = ExtendedRandom(Random(978546))
        val p = 10.0
        val s = generateSequence { r.geometric(p) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals((1.0 - p) / p.pow(2), s.variance, 10.0)
        assertEquals(1.0 / p, s.mean, 5.0)
    }

    @Test
    fun exponential() {
        val r = ExtendedRandom(Random(100))
        val rate = 10.0
        val s = generateSequence { r.exponential(rate) }
                .take(200)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(1.0 / rate.pow(2), s.variance, 0.5)
        assertEquals(1.0 / rate, s.mean, 0.1)
    }
}
