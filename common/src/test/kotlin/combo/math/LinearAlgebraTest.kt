package combo.math

import combo.test.assertContentEquals
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class LinearAlgebraTest {

    @Test
    fun transpose() {
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))

        val expected = arrayOf(
                floatArrayOf(1.0f, 4.0f, 7.0f),
                floatArrayOf(2.0f, 5.0f, 8.0f),
                floatArrayOf(3.0f, 6.0f, 9.0f))
        assertContentEquals(expected, A.T)
        assertContentEquals(A, A.T.T)
        A.transpose()
        assertContentEquals(expected, A)
    }

    @Test
    fun sum() {
        val v = Vector(3)
        assertEquals(3.0f, (v + 1.0f).sum())
    }

    @Test
    fun timesVectorWithScalar() {
        val s = 5.0f
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        assertContentEquals(floatArrayOf(-5.0f, 10.0f, 0.0f), v * s)
        assertContentEquals(floatArrayOf(-5.0f, 10.0f, 0.0f), s * v)
    }

    @Test
    fun divVectorWithScalar() {
        val s = 5.0f
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        assertContentEquals(floatArrayOf(-0.2f, 0.4f, 0.0f), v / s)
        assertContentEquals(floatArrayOf(-5.0f, 2.5f, Float.POSITIVE_INFINITY), s / v)
    }

    @Test
    fun plusVectorWithScalar() {
        val s = 5.0f
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        assertContentEquals(floatArrayOf(4.0f, 7.0f, 5.0f), v + s)
        assertContentEquals(floatArrayOf(4.0f, 7.0f, 5.0f), s + v)
    }

    @Test
    fun minusVectorWithScalar() {
        val s = 5.0f
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        assertContentEquals(floatArrayOf(-6.0f, -3.0f, -5.0f), v - s)
        assertContentEquals(floatArrayOf(6.0f, 3.0f, 5.0f), s - v)
    }

    @Test
    fun dotProduct() {
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        val u = floatArrayOf(0.0f, 3.0f, 4.0f)
        assertEquals(6.0f, u dot v)
        assertEquals(6.0f, v dot u)
    }

    @Test
    fun outerProduct() {
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        val u = floatArrayOf(0.0f, 3.0f, 4.0f)
        val expected1 = arrayOf(
                floatArrayOf(0.0f, -3.0f, -4.0f),
                floatArrayOf(0.0f, 6.0f, 8.0f),
                floatArrayOf(0.0f, 0.0f, 0.0f))
        val outer1 = v outer u
        assertContentEquals(expected1, outer1)
        val expected2 = expected1.T
        val outer2 = u outer v
        assertContentEquals(expected2, outer2)
    }

    @Test
    fun timesLeftMatrix() {
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))
        val result = v * A
        assertContentEquals(floatArrayOf(7.0f, 8.0f, 9.0f), result)
    }

    @Test
    fun timesRightMatrix() {
        val v = floatArrayOf(-1.0f, 2.0f, 0.0f)
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))
        val result = A * v
        assertContentEquals(floatArrayOf(3.0f, 6.0f, 9.0f), result)
    }

    @Test
    fun timesMatrixWithScalar() {
        val s = 5.0f
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))
        val expected = arrayOf(
                floatArrayOf(5.0f, 10.0f, 15.0f),
                floatArrayOf(20.0f, 25.0f, 30.0f),
                floatArrayOf(35.0f, 40.0f, 45.0f))
        val actual1 = (s * A)
        val actual = (A * s)
        assertContentEquals(expected, actual1)
        assertContentEquals(expected, actual)
    }

    @Test
    fun plusMatrixWithScalar() {
        val s = 5.0f
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))
        val expected = arrayOf(
                floatArrayOf(6.0f, 7.0f, 8.0f),
                floatArrayOf(9.0f, 10.0f, 11.0f),
                floatArrayOf(12.0f, 13.0f, 14.0f))
        assertContentEquals(expected, s + A)
        assertContentEquals(expected, A + s)
    }

    @Test
    fun minusMatrixWithScalar() {
        val s = 5.0f
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))
        val expected1 = arrayOf(
                floatArrayOf(4.0f, 3.0f, 2.0f),
                floatArrayOf(1.0f, 0.0f, -1.0f),
                floatArrayOf(-2.0f, -3.0f, -4.0f))
        val expected2 = arrayOf(
                floatArrayOf(-4.0f, -3.0f, -2.0f),
                floatArrayOf(-1.0f, 0.0f, 1.0f),
                floatArrayOf(2.0f, 3.0f, 4.0f))
        assertContentEquals(expected1, s - A)
        assertContentEquals(expected2, A - s)
    }

    @Test
    fun divMatrixWithScalar() {
        val s = 5.0f
        val A = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f, 9.0f))
        val expected1 = arrayOf(
                floatArrayOf(5 / 1.0f, 5 / 2.0f, 5 / 3.0f),
                floatArrayOf(5 / 4.0f, 5 / 5.0f, 5 / 6.0f),
                floatArrayOf(5 / 7.0f, 5 / 8.0f, 5 / 9.0f))
        val expected2 = arrayOf(
                floatArrayOf(0.2f, 0.4f, 0.6f),
                floatArrayOf(0.8f, 1.0f, 1.2f),
                floatArrayOf(1.4f, 1.6f, 1.8f))
        assertContentEquals(expected1, (s / A))
        assertContentEquals(expected2, (A / s))
    }

    @Test
    fun basicShermanUpdater() {
        val H = matrix(6)
        for (i in 0 until 6)
            H[i, i] = 1.0f
        val x = FloatArray(6) { 0.5f }
        x[2] = 0.0f
        val inc = H.shermanUpdater(x)
        val expectedInc = FloatArray(6) { 1.0f / 3.0f }
        expectedInc[2] = 0.0f
        assertContentEquals(expectedInc, inc)
    }

    @Test
    fun fullShermanUpdater() {
        val H = arrayOf(
                floatArrayOf(1.0565f, 0.3456f, -0.4646f, 1.8587f),
                floatArrayOf(0.3456f, 0.5910f, -0.1395f, 0.6277f),
                floatArrayOf(-0.4646f, -0.1395f, 0.2371f, -0.8419f),
                floatArrayOf(1.8587f, 0.6277f, -0.8419f, 4.2134f))
        val x = floatArrayOf(1.0f, 0.0f, 1.0f, 1.0f) * sqrt(0.5f)

        val inc = H.shermanUpdater(x)

        val expectedInc = floatArrayOf(0.8351f, 0.2841f, -0.3644f, 1.7823f)
        assertContentEquals(expectedInc, inc, 1e-3f)
        H.sub(inc outer inc)

        val expected = arrayOf(
                floatArrayOf(0.3591f, 0.1083f, -0.1603f, 0.3703f),
                floatArrayOf(0.1083f, 0.5103f, -0.0360f, 0.1213f),
                floatArrayOf(-0.1603f, -0.0360f, 0.1042f, -0.1923f),
                floatArrayOf(0.3703f, 0.1213f, -0.1923f, 1.0367f))

        assertContentEquals(expected, H, 1e-3f)
    }

    @Test
    fun cholDowndate() {
        val H = arrayOf(
                floatArrayOf(1.0565f, 0.3456f, -0.4646f, 1.8587f),
                floatArrayOf(0.3456f, 0.5910f, -0.1395f, 0.6277f),
                floatArrayOf(0.4646f, -0.1395f, 0.2371f, -0.8419f),
                floatArrayOf(.8587f, 0.6277f, -0.8419f, 4.2134f))

        val x = floatArrayOf(1.0f, 0.0f, 1.0f, 1.0f) * sqrt(0.5f)
        val L = arrayOf(
                floatArrayOf(2.1585f, 0.7060f, -0.9493f, 3.7975f),
                floatArrayOf(0.0f, 1.4519f, 0.0378f, 0.0599f),
                floatArrayOf(0.0f, 0.0f, 0.3780f, -0.2912f),
                floatArrayOf(0.0f, 0.0f, 0.0f, 2.0179f))

        val inc = H.shermanUpdater(x)

        L.cholDowndate(inc)

        // tested using matlabs cholupdate(L,inc,'-')

        val expected = arrayOf(
                floatArrayOf(1.989f, 0.6459f, -1.0100f, 3.5109f),
                floatArrayOf(0.0f, 1.4519f, 0.0349f, 0.0602f),
                floatArrayOf(0.0f, 0.0f, 0.1479f, -0.6721f),
                floatArrayOf(0.0f, 0.0f, 0.0f, 1.9246f))

        assertContentEquals(expected, L, 1e-4f)
    }
}

