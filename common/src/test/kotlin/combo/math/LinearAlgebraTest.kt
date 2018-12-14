package combo.math

import combo.test.assertContentEquals
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class LinearAlgebraTest {

    @Test
    fun transpose() {
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))

        val expected = arrayOf(
                doubleArrayOf(1.0, 4.0, 7.0),
                doubleArrayOf(2.0, 5.0, 8.0),
                doubleArrayOf(3.0, 6.0, 9.0))
        assertContentEquals(expected, A.T)
        assertContentEquals(A, A.T.T)
        A.transpose()
        assertContentEquals(expected, A)
    }

    @Test
    fun sum() {
        val v = Vector(3)
        assertEquals(3.0, (v + 1.0).sum())
    }

    @Test
    fun timesVectorWithScalar() {
        val s = 5.0
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        assertContentEquals(doubleArrayOf(-5.0, 10.0, 0.0), v * s)
        assertContentEquals(doubleArrayOf(-5.0, 10.0, 0.0), s * v)
    }

    @Test
    fun divVectorWithScalar() {
        val s = 5.0
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        assertContentEquals(doubleArrayOf(-0.2, 0.4, 0.0), v / s)
        assertContentEquals(doubleArrayOf(-5.0, 2.5, Double.POSITIVE_INFINITY), s / v)
    }

    @Test
    fun plusVectorWithScalar() {
        val s = 5.0
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        assertContentEquals(doubleArrayOf(4.0, 7.0, 5.0), v + s)
        assertContentEquals(doubleArrayOf(4.0, 7.0, 5.0), s + v)
    }

    @Test
    fun minusVectorWithScalar() {
        val s = 5.0
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        assertContentEquals(doubleArrayOf(-6.0, -3.0, -5.0), v - s)
        assertContentEquals(doubleArrayOf(6.0, 3.0, 5.0), s - v)
    }

    @Test
    fun dotProduct() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        val u = doubleArrayOf(0.0, 3.0, 4.0)
        assertEquals(6.0, u dot v)
        assertEquals(6.0, v dot u)
    }

    @Test
    fun outerProduct() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        val u = doubleArrayOf(0.0, 3.0, 4.0)
        val expected1 = arrayOf(
                doubleArrayOf(0.0, -3.0, -4.0),
                doubleArrayOf(0.0, 6.0, 8.0),
                doubleArrayOf(0.0, 0.0, 0.0))
        val outer1 = v outer u
        assertContentEquals(expected1, outer1)
        val expected2 = expected1.T
        val outer2 = u outer v
        assertContentEquals(expected2, outer2)
    }

    @Test
    fun timesLeftMatrix() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))
        val result = v * A
        assertContentEquals(doubleArrayOf(7.0, 8.0, 9.0), result)
    }

    @Test
    fun timesRightMatrix() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0)
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))
        val result = A * v
        assertContentEquals(doubleArrayOf(3.0, 6.0, 9.0), result)
    }

    @Test
    fun timesMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))
        val expected = arrayOf(
                doubleArrayOf(5.0, 10.0, 15.0),
                doubleArrayOf(20.0, 25.0, 30.0),
                doubleArrayOf(35.0, 40.0, 45.0))
        val actual1 = (s * A)
        val actual = (A * s)
        assertContentEquals(expected, actual1)
        assertContentEquals(expected, actual)
    }

    @Test
    fun plusMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))
        val expected = arrayOf(
                doubleArrayOf(6.0, 7.0, 8.0),
                doubleArrayOf(9.0, 10.0, 11.0),
                doubleArrayOf(12.0, 13.0, 14.0))
        assertContentEquals(expected, s + A)
        assertContentEquals(expected, A + s)
    }

    @Test
    fun minusMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))
        val expected1 = arrayOf(
                doubleArrayOf(4.0, 3.0, 2.0),
                doubleArrayOf(1.0, 0.0, -1.0),
                doubleArrayOf(-2.0, -3.0, -4.0))
        val expected2 = arrayOf(
                doubleArrayOf(-4.0, -3.0, -2.0),
                doubleArrayOf(-1.0, 0.0, 1.0),
                doubleArrayOf(2.0, 3.0, 4.0))
        assertContentEquals(expected1, s - A)
        assertContentEquals(expected2, A - s)
    }

    @Test
    fun divMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0))
        val expected1 = arrayOf(
                doubleArrayOf(5 / 1.0, 5 / 2.0, 5 / 3.0),
                doubleArrayOf(5 / 4.0, 5 / 5.0, 5 / 6.0),
                doubleArrayOf(5 / 7.0, 5 / 8.0, 5 / 9.0))
        val expected2 = arrayOf(
                doubleArrayOf(0.2, 0.4, 0.6),
                doubleArrayOf(0.8, 1.0, 1.2),
                doubleArrayOf(1.4, 1.6, 1.8))
        assertContentEquals(expected1, (s / A))
        assertContentEquals(expected2, (A / s))
    }

    @Test
    fun basicShermanUpdater() {
        val H = matrix(6)
        for (i in 0 until 6)
            H[i, i] = 1.0
        val x = DoubleArray(6) { 0.5 }
        x[2] = 0.0
        val inc = H.shermanUpdater(x)
        val expectedInc = DoubleArray(6) { 1.0 / 3.0 }
        expectedInc[2] = 0.0
        assertContentEquals(expectedInc, inc)
    }

    @Test
    fun fullShermanUpdater() {
        val H = arrayOf(
                doubleArrayOf(1.0565, 0.3456, -0.4646, 1.8587),
                doubleArrayOf(0.3456, 0.5910, -0.1395, 0.6277),
                doubleArrayOf(-0.4646, -0.1395, 0.2371, -0.8419),
                doubleArrayOf(1.8587, 0.6277, -0.8419, 4.2134))
        val x = doubleArrayOf(1.0, 0.0, 1.0, 1.0) * sqrt(0.5)

        val inc = H.shermanUpdater(x)

        val expectedInc = doubleArrayOf(0.8351, 0.2841, -0.3644, 1.7823)
        assertContentEquals(expectedInc, inc, 1e-3)
        H.sub(inc outer inc)

        val expected = arrayOf(
                doubleArrayOf(0.3591, 0.1083, -0.1603, 0.3703),
                doubleArrayOf(0.1083, 0.5103, -0.0360, 0.1213),
                doubleArrayOf(-0.1603, -0.0360, 0.1042, -0.1923),
                doubleArrayOf(0.3703, 0.1213, -0.1923, 1.0367))

        assertContentEquals(expected, H, 1e-3)
    }

    @Test
    fun cholDowndate() {
        val H = arrayOf(
                doubleArrayOf(1.0565, 0.3456, -0.4646, 1.8587),
                doubleArrayOf(0.3456, 0.5910, -0.1395, 0.6277),
                doubleArrayOf(0.4646, -0.1395, 0.2371, -0.8419),
                doubleArrayOf(.8587, 0.6277, -0.8419, 4.2134))

        val x = doubleArrayOf(1.0, 0.0, 1.0, 1.0) * sqrt(0.5)
        val L = arrayOf(
                doubleArrayOf(2.1585, 0.7060, -0.9493, 3.7975),
                doubleArrayOf(0.0, 1.4519, 0.0378, 0.0599),
                doubleArrayOf(0.0, 0.0, 0.3780, -0.2912),
                doubleArrayOf(0.0, 0.0, 0.0, 2.0179))

        val inc = H.shermanUpdater(x)

        L.cholDowndate(inc)

        // tested using matlabs cholupdate(L,inc,'-')

        val expected = arrayOf(
                doubleArrayOf(1.989, 0.6459, -1.0100, 3.5109),
                doubleArrayOf(0.0, 1.4519, 0.0349, 0.0602),
                doubleArrayOf(0.0, 0.0, 0.1479, -0.6721),
                doubleArrayOf(0.0, 0.0, 0.0, 1.9246))

        assertContentEquals(expected, L, 1e-4)
    }
}

