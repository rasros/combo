package combo.math

import combo.test.assertEquals
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosteriorsTest {
    @Test
    fun binomialUpdate() {
        val stat = BinomialPosterior.defaultPrior()
        val r = Random(1024)
        val s1 = generateSequence { BinomialPosterior.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(.5, s1.mean, 0.1)
        assertEquals(25.0, s1.sum, 5.0)
        doubleArrayOf(1.0, 1.0, 0.0).forEach { BinomialPosterior.update(stat, it) }
        val s2 = generateSequence { BinomialPosterior.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(3 / 5.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun poissonPriorFinite() {
        val p = PoissonPosterior
        val stat = p.defaultPrior()
        val r = Random(6768)
        val sum = generateSequence { p.sample(r, stat) }.take(50).sumByDouble { it }
        assertTrue(sum.isFinite())
        assertFalse(sum.isNaN())
    }

    @Test
    fun poissonUpdate() {
        val p = PoissonPosterior
        val stat = p.defaultPrior()
        val r = Random(19)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(1.0, s1.mean, 5.0) // posterior has variance=100, so prior estimate is bad
        doubleArrayOf(3.0, 4.0, 0.0, 5.0, 0.0, 1.0, 0.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(stat.mean, s2.mean, 0.5)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun geometricUpdate() {
        val p = GeometricPosterior
        val stat = p.defaultPrior()
        val r = Random(46978)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(0.5, s1.mean, 0.1)
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 3.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(1 / stat.mean, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun normalPriorSignUnbiased() {
        val p = GaussianPosterior
        val stat = p.defaultPrior()
        val r = Random(893)
        val dataSample = generateSequence { p.sample(r, stat) }.take(100).sample(FullSample())
        val neg = dataSample.collect().count { it < 0 }
        val pos = dataSample.collect().count { it > 0 }
        assertEquals(neg.toDouble(), pos.toDouble(), 30.0)
    }

    @Test
    fun normalPriorFinite() {
        val p = GaussianPosterior
        val stat = p.defaultPrior()
        val r = Random(46978)
        generateSequence { p.sample(r, stat) }.take(50).forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
        }
    }

    @Test
    fun normalUpdate() {
        val p = GaussianPosterior
        val stat = p.defaultPrior()
        val r = Random(46978)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(3.0, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun logNormalPriorFinite() {
        val p = LogNormalPosterior
        val stat = p.defaultPrior()
        val r = Random(89776)
        generateSequence { p.sample(r, stat) }.take(50).forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
        }
    }

    @Test
    fun logNormalUpdate() {
        val p = LogNormalPosterior
        val stat = p.defaultPrior()
        val r = Random(979)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(3.0, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun exponentialUpdate() {
        val p = ExponentialPosterior
        val stat = p.defaultPrior()
        val r = Random(75)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(1 / 3.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun gammaScaleUpdate() {
        val p = GammaScalePosterior(fixedShape = 2.0)
        val stat = p.defaultPrior()
        val r = Random(1023)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(2.0 / 3.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun varianceBound() {
        for ((i, posterior) in arrayOf(PoissonPosterior, ExponentialPosterior, GaussianPosterior, GammaScalePosterior(0.1),
                BinomialPosterior, GeometricPosterior, LogNormalPosterior).withIndex()) {
            val prior = posterior.defaultPrior()
            assertTrue(prior.variance.isFinite(), i.toString())
            assertTrue(prior.mean.isFinite(), i.toString())
        }
        for ((i, posterior) in arrayOf(ExponentialPosterior, GaussianPosterior, GammaScalePosterior(0.1),
                BinomialPosterior, LogNormalPosterior).withIndex()) {
            val prior = posterior.defaultPrior()
            assertTrue(prior.variance > 0.0, i.toString())
        }

    }
}
