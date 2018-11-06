package combo.math

import combo.test.assertEquals
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test

class TransformsTest {
    @Test
    fun identityBacktransform() {
        val rng = ExtendedRandom(Random(1230))
        backtransformTest(identity(), generateSequence { rng.nextGaussian() })
    }

    @Test
    fun shiftBacktransform() {
        val rng = ExtendedRandom(Random(1230))
        backtransformTest(shift(1.0), generateSequence { rng.nextGaussian() })
    }

    @Test
    fun scaleBacktransform() {
        val rng = ExtendedRandom(Random(5))
        backtransformTest(scale(1.0), generateSequence { rng.nextGaussian() })
    }

    @Test
    fun standardBacktransform() {
        val r1 = ExtendedRandom(Random(12))
        val r2 = ExtendedRandom(Random(-12))
        backtransformTest(standard(0.0, 1.0), generateSequence { r1.nextGaussian() })
        backtransformTest(standard(2.0, 3.0), generateSequence { r2.nextGaussian() })
    }

    @Test
    fun arcSineBacktransform() {
        val rng = Random(124)
        backtransformTest(arcSine(), generateSequence { rng.nextDouble() })
    }

    @Test
    fun logBacktransform() {
        val r = Random(10)
        backtransformTest(log(), generateSequence { 1.0 + r.nextDouble() })
    }

    @Test
    fun squareRootBacktransform() {
        val r = Random(113)
        backtransformTest(squareRoot(), generateSequence { r.nextDouble() })
    }

    @Test
    fun logitInverse() {
        val rng = Random(102310)
        inverseTest(logit(), generateSequence { rng.nextDouble() })
    }

    @Test
    fun clogLogInverse() {
        val rng = Random(1023)
        inverseTest(clogLog(), generateSequence { rng.nextDouble() })
    }

    @Test
    fun inverseBacktransform() {
        val rng = Random(78524)
        backtransformTest(inverse(), generateSequence { 1 + rng.nextDouble() })
    }

    @Test
    fun negativeInverseBacktransform() {
        val rng = ExtendedRandom(Random(54))
        // TODO backtransform
        inverseTest(negativeInverse(), generateSequence { rng.nextGaussian() })
    }

    @Test
    fun inverseSquaredBacktransform() {
        val rng = ExtendedRandom(Random(54))
        // Using folded normal to test here
        backtransformTest(inverseSquared(), generateSequence { abs(rng.nextGaussian()) })
    }

    private fun inverseTest(transform: Transform, seq: Sequence<Double>) {
        seq.take(100).forEach {
            assertEquals(it, transform.inverse(transform.apply(it)), 1e-6)
        }
    }

    private fun backtransformTest(transform: Transform, seq: Sequence<Double>) {
        val transformed = RunningVariance()
        val original = RunningVariance()
        seq.take(100).forEach {
            assertEquals(it, transform.inverse(transform.apply(it)), 1e-6)
            original.accept(it)
            transformed.accept(transform.apply(it))
        }
        val backtransformed = transform.backtransform(transformed)
        assertEquals(original.mean, backtransformed.mean, max(abs(transformed.mean), abs(original.mean)) * 0.5)
        assertEquals(original.variance, backtransformed.variance, max(abs(transformed.variance), abs(original.variance)))
    }
}

