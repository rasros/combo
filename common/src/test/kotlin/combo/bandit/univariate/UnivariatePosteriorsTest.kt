package combo.bandit.univariate

import combo.math.*
import combo.test.assertEquals
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class UnivariatePosteriorsTest {

    private val pool = PooledVarianceEstimator(RunningVariance())

    @Test
    fun binomialUpdate() {
        val stat = BinomialPosterior.defaultPrior()
        val r = Random(1024)
        val s1 = generateSequence { BinomialPosterior.sample(stat, pool, r) }.take(100).sample(RunningVariance())
        assertEquals(.5, s1.mean, 0.1)
        assertEquals(50.0, s1.sum, 5.0)
        doubleArrayOf(1.0, 1.0, 0.0).forEach { BinomialPosterior.update(stat, it) }
        val s2 = generateSequence { BinomialPosterior.sample(stat, pool, r) }.take(100).sample(RunningVariance())
        assertEquals(3 / 5.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun poissonUpdate() {
        val p = PoissonPosterior
        val stat = p.defaultPrior()
        val r = Random(19)
        val s1 = generateSequence { p.sample(stat, pool, r) }.take(100).sample(RunningVariance())
        assertEquals(1.0, s1.mean, 5.0) // posterior has variance=100, so prior estimate is bad
        doubleArrayOf(3.0, 4.0, 0.0, 5.0, 0.0, 1.0, 0.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(stat, pool, r) }.take(100).sample(RunningVariance())
        assertEquals(stat.mean, s2.mean, 0.5)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun geometricUpdate() {
        val p = GeometricPosterior
        val stat = p.defaultPrior()
        val r = Random(46978)
        val s1 = generateSequence { p.sample(stat, pool, r) }.take(100).sample(RunningVariance())
        assertEquals(0.5, s1.mean, 0.1)
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 3.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(stat, pool, r) }.take(100).sample(RunningVariance())
        assertEquals(1 / stat.mean, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun normalPriorSignUnbiased() {
        val p = NormalPosterior
        val stat = p.defaultPrior()
        val r = Random(893)
        val dataSample = generateSequence { p.sample(stat, pool, r) }.take(100).sample(FullSample())
        val neg = dataSample.toArray().count { it < 0 }
        val pos = dataSample.toArray().count { it > 0 }
        assertEquals(neg.toDouble(), pos.toDouble(), 30.0)
    }

    @Test
    fun normalUpdate() {

        fun test(data: DoubleArray, rng: Random) {
            // Ignoring the priors here
            val v = RunningVariance()
            data.forEach { v.accept(it) }
            val s = generateSequence { NormalPosterior.sample(v, pool, rng) }.take(100).sample(RunningVariance())
            assertEquals(v.mean, s.mean, 0.1)

            val alpha = v.nbrWeightedSamples / 2.0
            val beta = v.squaredDeviations / 2.0
            val lambda = v.nbrWeightedSamples

            assertEquals(s.variance, (beta / (alpha - 1)) / lambda, 1.0 / lambda)
        }
        test(doubleArrayOf(1.0, 2.0, 3.0), Random(0))
        test(doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0), Random(0))
        test(generateSequence { Random.nextNormal() }.take(50).toList().toDoubleArray(), Random(0))
    }

    @Test
    fun logNormalUpdate() {
        val p = LogNormalPosterior
        val stat = p.defaultPrior()
        val r = Random
        val mean = 5.0
        val variance = 10.0
        for (i in 1..1) p.update(stat, r.nextLogNormal(mean, variance))
        val v = RunningVariance()
        for (i in 1..1000000) v.accept(p.sample(stat, pool, r))
    }

    @Test
    fun exponentialUpdate() {
        val p = ExponentialPosterior
        val stat = p.defaultPrior()
        val r = Random(75)
        val s1 = generateSequence { p.sample(stat, pool, r) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(stat, pool, r) }.take(50).sample(RunningVariance())
        assertEquals(1 / 3.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun gammaScaleUpdate() {
        val p = GammaScalePosterior(fixedShape = 2.0)
        val stat = p.defaultPrior()
        val r = Random(1023)
        val s1 = generateSequence { p.sample(stat, pool, r) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(stat, pool, r) }.take(50).sample(RunningVariance())
        assertEquals(2.0 / 3.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }


    @Test
    @Ignore
    fun properPriors() {
        for ((i, posterior) in arrayOf(PoissonPosterior, ExponentialPosterior, NormalPosterior, GammaScalePosterior(0.1),
                BinomialPosterior, GeometricPosterior, LogNormalPosterior).withIndex()) {
            val prior = posterior.defaultPrior()
            assertTrue(prior.variance.isFinite(), i.toString())
            assertTrue(prior.mean.isFinite(), i.toString())
            val s = RunningVariance()
            //TODO generateSequence { posterior.sample(prior, pool, Random) }.take(50).forEach { s.accept(it) }
            assertTrue(s.variance.isFinite(), i.toString())
            assertTrue(s.variance > 0, i.toString())
            assertTrue(s.mean.isFinite(), i.toString())
        }
        for ((i, posterior) in arrayOf(NormalPosterior, LogNormalPosterior).withIndex()) {
            val prior = posterior.defaultPrior()
            assertTrue(prior.variance > 0.0, i.toString())
        }
    }

    // TODO tests for hierarchical
}
