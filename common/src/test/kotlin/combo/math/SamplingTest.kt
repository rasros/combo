package combo.math

import combo.test.assertEquals
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.*

class SamplingTest {
    @Test
    fun fixedSeed() {
        val r1 = Rng(0)
        val r2 = Rng(0)
        for (i in 0..100) {
            assertEquals(r1.boolean(), r2.boolean())
            assertEquals(r1.int(), r2.int())
            assertEquals(r1.long(), r2.long())
            assertEquals(r1.double(), r2.double())
            assertEquals(r1.gaussian(), r2.gaussian())
        }
    }

    @Test
    fun rngSequence() {

    }

    @Test
    fun intBound() {
        val r = Rng(1)
        var max = 0
        var min = 1
        generateSequence { r.int(10) }.take(100).forEach { max = max(max, it); min = min(min, it) }
        assertTrue(max < 10)
        assertTrue(min >= 0)
    }

    @Test
    fun longBound() {
        val r = Rng(2)
        var max = 0L
        var min = 1L
        generateSequence { r.long(10) }.take(100).forEach { max = max(max, it); min = min(min, it) }
        assertTrue(max < 10L)
        assertTrue(min >= 0L)
    }

    @Test
    fun boolean() {
        val r = Rng(1203)
        var c = 0
        while (r.boolean() || c++ > 100)
            while (!r.boolean() || c++ > 100)
                return
        fail()
    }

    @Test
    fun gaussianStats() {
        val r = Rng(120)
        val s = generateSequence { r.gaussian(mean = 1.0, std = sqrt(2.0)) }.take(1000).sample(RunningVariance())
        assertEquals(2.0, s.variance, 0.2)
        assertEquals(1.0, s.mean, 0.2)
    }

    @Test
    fun gammaShapeLessThan1() {
        val r = Rng(100)
        val shape = 0.1
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 0.2)
        assertEquals(shape * scale, s.mean, 0.1)
    }

    @Test
    fun gammaShapeEquals1() {
        val r = Rng(12934)
        val shape = 1.0
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 0.2)
        assertEquals(shape * scale, s.mean, 0.1)
    }

    @Test
    fun gammaShapeGreaterThan1() {
        val r = Rng(12934)
        val shape = 10.0
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 2.0)
        assertEquals(shape * scale, s.mean, 1.0)
    }

    @Test
    fun gammaShapeHuge() {
        val r = Rng(12934)
        val shape = 100.0 // will trigger gaussian approximation
        val scale = 1.0
        val s = generateSequence { r.gamma(shape, scale) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(shape * scale.pow(2), s.variance, 20.0)
        assertEquals(shape * scale, s.mean, 10.0)
    }

    @Test
    fun betaUniform() {
        val r = Rng(1023)
        val s = generateSequence { r.beta(1.0, 1.0) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals(1 / 12.0, s.variance, 0.1)
        assertEquals(0.5, s.mean, 0.1)
    }

    @Test
    fun betaSkewedDown() {
        val r = Rng(12410)
        val a = 1.0
        val b = 10.0
        val s = generateSequence { r.beta(a, b) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals((a * b) / ((a + b).pow(2) * (a + b + 1)), s.variance, 0.1)
        assertEquals(a / (a + b), s.mean, 0.1)
    }

    @Test
    fun betaSkewedUp() {
        val r = Rng(-10)
        val a = 10.0
        val b = 1.0
        val s = generateSequence { r.beta(a, b) }
                .take(100)
                .map { assertFalse(it <= 0, "$it"); assertFalse(it >= 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals((a * b) / ((a + b).pow(2) * (a + b + 1)), s.variance, 0.1)
        assertEquals(a / (a + b), s.mean, 0.1)
    }

    @Test
    fun poissonSmall() {
        val r = Rng(5468)
        val lambda = 0.1
        val s = generateSequence { r.poisson(lambda) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 0.1)
        assertEquals(lambda, s.mean, 0.05)
    }

    @Test
    fun poissonMedium() {
        val r = Rng(1546)
        val lambda = 2.0
        val s = generateSequence { r.poisson(lambda) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 1.0)
        assertEquals(lambda, s.mean, 0.5)
    }

    @Test
    fun poissonHuge() {
        val r = Rng(68)
        val lambda = 100.0 // will trigger gaussian approximation
        val s = generateSequence { r.poisson(lambda) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(lambda, s.variance, 20.0)
        assertEquals(lambda, s.mean, 5.0)
    }

    @Test
    fun bernoulli() {
        val r = Rng(568)
        val p = 0.1
        val s = generateSequence { r.binomial(p) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); assertFalse(it > 1, "$it"); it }
                .sample(RunningVariance())
        assertEquals(p * (1 - p), s.variance, 0.2)
        assertEquals(p, s.mean, 0.1)
    }

    @Test
    fun binomialHuge() {
        val r = Rng(789)
        val p = 0.2
        val n = 100
        val s = generateSequence { r.binomial(p, n) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(n * p * (1 - p), s.variance, 10.0)
        assertEquals(n * p, s.mean, 5.0)
    }

    @Test
    fun geometric() {
        val r = Rng(978546)
        val p = 10.0
        val s = generateSequence { r.geometric(p) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals((1.0 - p) / p.pow(2), s.variance, 10.0)
        assertEquals(1.0 / p, s.mean, 5.0)
    }

    @Test
    fun exponential() {
        val r = Rng(100)
        val rate = 10.0
        val s = generateSequence { r.exponential(rate) }
                .take(100)
                .map { assertFalse(it < 0, "$it"); it }
                .sample(RunningVariance())
        assertEquals(1.0 / rate.pow(2), s.variance, 0.5)
        assertEquals(1.0 / rate, s.mean, 0.1)
    }
}
