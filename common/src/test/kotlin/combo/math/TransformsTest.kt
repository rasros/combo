package combo.math

import combo.test.assertEquals
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test

class TransformsTest {
    @Test
    fun identityBacktransform() {
        val rng = Rng(1230)
        backtransformTest(identity(), generateSequence { rng.gaussian() })
    }

    @Test
    fun shiftBacktransform() {
        val rng = Rng(1230)
        backtransformTest(shift(1.0), generateSequence { rng.gaussian() })
    }

    @Test
    fun scaleBacktransform() {
        val rng = Rng(5)
        backtransformTest(scale(1.0), generateSequence { rng.gaussian() })
    }

    @Test
    fun standardBacktransform() {
        val r1 = Rng(12)
        val r2 = Rng(-12)
        backtransformTest(standard(0.0, 1.0), generateSequence { r1.gaussian() })
        backtransformTest(standard(2.0, 3.0), generateSequence { r2.gaussian() })
    }

    @Test
    fun arcSineBacktransform() {
        val rng = Rng(124)
        backtransformTest(arcSine(), generateSequence { rng.double() })
    }

    @Test
    fun logBacktransform() {
        val r = Rng(10)
        backtransformTest(log(), generateSequence { 1.0 + r.double() })
    }

    @Test
    fun squareRootBacktransform() {
        val r = Rng(113)
        backtransformTest(squareRoot(), generateSequence { r.double() })
    }

    @Test
    fun logitInverse() {
        val rng = Rng(102310)
        inverseTest(logit(), generateSequence { rng.double() })
    }

    @Test
    fun clogLogInverse() {
        val rng = Rng(1023)
        inverseTest(clogLog(), generateSequence { rng.double() })
    }

    @Test
    fun inverseBacktransform() {
        val rng = Rng(78524)
        backtransformTest(inverse(), generateSequence { 1 + rng.double() })
    }

    @Test
    fun negativeInverseBacktransform() {
        val rng = Rng(54)
        // TODO backtransform
        inverseTest(negativeInverse(), generateSequence { rng.gaussian() })
    }

    @Test
    fun inverseSquaredBacktransform() {
        val rng = Rng(54)
        // Using folded normal to test here
        backtransformTest(inverseSquared(), generateSequence { abs(rng.gaussian()) })
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

