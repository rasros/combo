package combo.math

import combo.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosteriorsTest {
    @Test
    fun binomialUpdate() {
        val p = binomial()
        val stat = p.defaultPrior()
        val r = Rng(1024)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(0.5, s1.mean, 0.1)
        doubleArrayOf(1.0, 1.0, 0.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(3 / 5.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun poissonPriorFinite() {
        val p = poisson()
        val stat = p.defaultPrior()
        val r = Rng(6768)
        generateSequence { p.sample(r, stat) }.take(50).forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
        }
    }

    @Test
    fun poissonUpdate() {
        val p = poisson()
        val stat = p.defaultPrior()
        val r = Rng(19)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(1.0, s1.mean, 5.0) // posterior has variance=100, so prior estimate is bad
        doubleArrayOf(3.0, 4.0, 0.0, 5.0, 0.0, 1.0, 0.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(stat.mean, s2.mean, 0.5)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun geometricUpdate() {
        val p = geometric()
        val stat = p.defaultPrior()
        val r = Rng(46978)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(0.5, s1.mean, 0.1)
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 3.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(1 / stat.mean, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun normalPriorSignUnbiased() {
        val p = normal()
        val stat = p.defaultPrior()
        val r = Rng(894)
        val dataSample = generateSequence { p.sample(r, stat) }.take(50).sample(FullSample())
        val neg = dataSample.collect().count { it < 0 }
        val pos = dataSample.collect().count { it > 0 }
        assertEquals(neg.toDouble(), pos.toDouble(), 10.0)
    }

    @Test
    fun normalPriorFinite() {
        val p = normal()
        val stat = p.defaultPrior()
        val r = Rng(46978)
        generateSequence { p.sample(r, stat) }.take(50).forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
        }
    }

    @Test
    fun normalUpdate() {
        val p = normal()
        val stat = p.defaultPrior()
        val r = Rng(46978)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(3.0, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun logNormalPriorFinite() {
        val p = logNormal()
        val stat = p.defaultPrior()
        val r = Rng(89776)
        generateSequence { p.sample(r, stat) }.take(50).forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
        }
    }

    @Test
    fun logNormalUpdate() {
        val p = logNormal()
        val stat = p.defaultPrior()
        val r = Rng(979)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(3.0, s2.mean, 0.2)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun exponentialUpdate() {
        val p = exponential()
        val stat = p.defaultPrior()
        val r = Rng(75)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(1 / 3.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }

    @Test
    fun gammaScaleUpdate() {
        val p = gammaScale(fixedShape = 2.0)
        val stat = p.defaultPrior()
        val r = Rng(1023)
        val s1 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        doubleArrayOf(3.0, 4.0, 1.0, 3.0, 6.0, 2.0, 2.0).forEach { p.update(stat, it) }
        val s2 = generateSequence { p.sample(r, stat) }.take(50).sample(RunningVariance())
        assertEquals(2.0 / 3.0, s2.mean, 0.1)
        assertTrue(s2.variance < s1.variance)
    }
}
