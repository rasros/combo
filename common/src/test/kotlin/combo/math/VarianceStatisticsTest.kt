package combo.math

import combo.test.assertEquals
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class VarianceStatisticsTest {

    @Test
    fun fixedEstimate() {
        val s = FixedVariance(1.0, 2.0, 10.0)
        assertEquals(sqrt(2.0), s.standardDeviation)
        assertEquals(10.0, s.sum())
        assertEquals(4.5, s.squaredDeviations)
        assertEquals(10, s.nbrSamples)
    }

    @Test
    fun incrementalVarianceFixed() {
        val s = RunningVariance()
        s.acceptAll(doubleArrayOf(1.0, 0.3, 0.2, 0.1))
        assertEquals(0.4, s.mean, 1e-6)
        assertEquals(1.0 / 6.0, s.variance, 1e-6)
    }

    @Test
    fun incrementalVarianceRandom() {
        val r = Random(100)
        val s = generateSequence { r.nextGaussian() }.take(200).sample(RunningVariance())
        assertEquals(0.0, s.mean, 0.1)
        assertEquals(1.0, s.variance, 0.2)
    }

    @Test
    fun decayingVarianceFixed() {
        val s = ExponentialDecayVariance(10)
        s.acceptAll(doubleArrayOf(1.0, 0.3, 0.2, 0.1))
        assertEquals(0.2, s.mean, 0.01)
        assertEquals(1.0 / 6.0, s.variance, 0.1)
    }

    @Test
    fun decayingVarianceRandom() {
        val r = Random(101)
        val s = generateSequence { r.nextGaussian() }.take(500).sample(ExponentialDecayVariance())
        assertEquals(0.0, s.mean, 0.1)
        assertEquals(1.0, s.variance, 0.2)
    }
}
