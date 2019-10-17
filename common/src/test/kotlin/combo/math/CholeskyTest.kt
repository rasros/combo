package combo.math

import combo.test.assertContentEquals
import kotlin.math.sqrt
import kotlin.test.Test

class CholeskyTest {
    @Test
    fun choleskyTest() {
        val H = FallbackMatrix(arrayOf(
                floatArrayOf(1.0565f, 0.3456f, -0.4646f, 1.8587f),
                floatArrayOf(0.3456f, 0.5910f, -0.1395f, 0.6277f),
                floatArrayOf(-0.4646f, -0.1395f, 0.2371f, -0.8419f),
                floatArrayOf(1.8587f, 0.6277f, -0.8419f, 4.2134f)))

        val L = arrayOf(
                floatArrayOf(1.0279f, 0.3362f, -0.4520f, 1.8083f),
                floatArrayOf(0.0f, 0.6913f, 0.0181f, 0.0285f),
                floatArrayOf(0.0f, 0.0f, 0.1799f, -0.1392f),
                floatArrayOf(0.0f, 0.0f, 0.0f, 0.9608f))

        val cholesky = H.cholesky().T
        assertContentEquals(L, cholesky.toArray(), 1e-3f)
    }

    @Test
    fun choleskyDowndate() {
        val H = FallbackMatrix(arrayOf(
                floatArrayOf(1.0565f, 0.3456f, -0.4646f, 1.8587f),
                floatArrayOf(0.3456f, 0.5910f, -0.1395f, 0.6277f),
                floatArrayOf(-0.4646f, -0.1395f, 0.2371f, -0.8419f),
                floatArrayOf(1.8587f, 0.6277f, -0.8419f, 4.2134f)))

        val x = FallbackVector(floatArrayOf(1.0f, 0.0f, 1.0f, 1.0f)) * sqrt(0.5f)
        val L = FallbackMatrix(arrayOf(
                floatArrayOf(1.0279f, 0.3362f, -0.4520f, 1.8083f),
                floatArrayOf(0.0f, 0.6913f, 0.0181f, 0.0285f),
                floatArrayOf(0.0f, 0.0f, 0.1799f, -0.1392f),
                floatArrayOf(0.0f, 0.0f, 0.0f, 0.9608f)))

        val t: Vector = H * x
        val inc = t.apply { divide(sqrt(1.0f + (t dot x))) }

        L.choleskyDowndate(inc)

        val expected = arrayOf(
                floatArrayOf(0.5993294f, 0.18070239f, -0.2674404f, 0.6179582f),
                floatArrayOf(0f, 0.69104266f, 0.017975843f, 0.013850121f),
                floatArrayOf(0f, 0f, 0.1798485f, -0.15207992f),
                floatArrayOf(0f, 0f, 0f, 0.79465646f))

        assertContentEquals(expected, L.toArray(), 1e-6f)
    }
}
