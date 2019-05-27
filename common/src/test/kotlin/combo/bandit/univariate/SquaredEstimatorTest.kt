package combo.bandit.univariate

import combo.math.nextNormal
import combo.math.sample
import combo.test.assertEquals
import kotlin.random.Random
import kotlin.test.Test

class SquaredEstimatorTest {
    @Test
    fun combine() {
        val rng = Random(0)
        val data1 = generateSequence { rng.nextNormal(2.0f, 4.0f) }.take(100).toList().toTypedArray()
        val data2 = generateSequence { rng.nextNormal(3.0f, 9.0f) }.take(100).toList().toTypedArray()
        val se1 = data1.asSequence().sample(SquaredEstimator())
        val se2 = data2.asSequence().sample(SquaredEstimator())
        val totalSeExpected = (data1 + data2).toList().shuffled().asSequence().sample(SquaredEstimator())
        val totalSeAcutal = se1.combine(se2)
        assertEquals(totalSeExpected.meanOfSquares, totalSeAcutal.meanOfSquares, 5.0f)
    }
}