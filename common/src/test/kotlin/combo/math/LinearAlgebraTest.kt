package combo.math

import combo.test.assertEquals
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class LinearAlgebraTest {

    @Test
    fun transpose() {
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()

        val expected = arrayOf(
                doubleArrayOf(1.0, 4.0, 7.0),
                doubleArrayOf(2.0, 5.0, 8.0),
                doubleArrayOf(3.0, 6.0, 9.0)).toMatrix()
        assertEquals(expected, A.T)
        assertEquals(A, A.T.T)
        A.transposed()
        assertEquals(expected, A)
    }

    @Test
    fun sum() {
        val v = Vector(3)
        assertEquals(3.0, (v + 1.0).sum())
    }

    @Test
    fun timesVectorWithScalar() {
        val s = 5.0
        val v = Vector(doubleArrayOf(-1.0, 2.0, 0.0))
        assertEquals(doubleArrayOf(-5.0, 10.0, 0.0).toVector(), v * s)
        assertEquals(doubleArrayOf(-5.0, 10.0, 0.0).toVector(), s * v)
    }

    @Test
    fun divVectorWithScalar() {
        val s = 5.0
        val v = Vector(doubleArrayOf(-1.0, 2.0, 0.0))
        assertEquals(doubleArrayOf(-0.2, 0.4, 0.0).toVector(), v / s)
        assertEquals(doubleArrayOf(-5.0, 2.5, Double.POSITIVE_INFINITY).toVector(), s / v)
    }

    @Test
    fun plusVectorWithScalar() {
        val s = 5.0
        val v = Vector(doubleArrayOf(-1.0, 2.0, 0.0))
        assertEquals(doubleArrayOf(4.0, 7.0, 5.0).toVector(), v + s)
        assertEquals(doubleArrayOf(4.0, 7.0, 5.0).toVector(), s + v)
    }

    @Test
    fun minusVectorWithScalar() {
        val s = 5.0
        val v = Vector(doubleArrayOf(-1.0, 2.0, 0.0))
        assertEquals(doubleArrayOf(-6.0, -3.0, -5.0).toVector(), v - s)
        assertEquals(doubleArrayOf(6.0, 3.0, 5.0).toVector(), s - v)
    }

    @Test
    fun dotProduct() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val u = doubleArrayOf(0.0, 3.0, 4.0).toVector()
        assertEquals(6.0, u dot v)
        assertEquals(6.0, v dot u)
    }

    @Test
    fun outerProduct() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val u = doubleArrayOf(0.0, 3.0, 4.0).toVector()
        val expected1 = arrayOf(
                doubleArrayOf(0.0, -3.0, -4.0),
                doubleArrayOf(0.0, 6.0, 8.0),
                doubleArrayOf(0.0, 0.0, 0.0))
        val outer1 = v outer u
        assertEquals(expected1, outer1.matrix, 1e-6)
        val expected2 = expected1.toMatrix().T.matrix
        val outer2 = u outer v
        assertEquals(expected2, outer2.matrix, 1e-6)
    }

    @Test
    fun timesVectorWithVector() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val u = doubleArrayOf(0.0, 3.0, 4.0).toVector()
        val result = v * u
        assertEquals(doubleArrayOf(0.0, 6.0, 0.0), result.array, 1e-6)
    }


    @Test
    fun divVectorWithVector() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val u = doubleArrayOf(1.0, 3.0, 4.0).toVector()
        val result = v / u
        assertEquals(doubleArrayOf(-1.0, 2 / 3.0, 0.0), result.array, 1e-6)
    }

    @Test
    fun minusVectorWithVector() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val u = doubleArrayOf(1.0, 3.0, 4.0).toVector()
        val result = v - u
        assertEquals(doubleArrayOf(-2.0, -1.0, -4.0), result.array, 1e-6)

    }

    @Test
    fun plusVectorWithVector() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val u = doubleArrayOf(1.0, 3.0, 4.0).toVector()
        val result = v + u
        assertEquals(doubleArrayOf(0.0, 5.0, 4.0), result.array, 1e-6)
    }

    @Test
    fun timesLeftMatrix() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()
        val result = v * A
        assertEquals(doubleArrayOf(7.0, 8.0, 9.0), result.array, 1e-6)
    }

    @Test
    fun timesRightMatrix() {
        val v = doubleArrayOf(-1.0, 2.0, 0.0).toVector()
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()
        val result = A * v
        assertEquals(doubleArrayOf(3.0, 6.0, 9.0), result.array, 1e-6)
    }

    @Test
    fun timesMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()
        val expected = arrayOf(
                doubleArrayOf(5.0, 10.0, 15.0),
                doubleArrayOf(20.0, 25.0, 30.0),
                doubleArrayOf(35.0, 40.0, 45.0))
        val actual1 = (s * A).matrix
        val actual = (A * s).matrix
        assertEquals(expected, actual1, 1e-6)
        assertEquals(expected, actual, 1e-6)
    }

    @Test
    fun plusMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()
        val expected = arrayOf(
                doubleArrayOf(6.0, 7.0, 8.0),
                doubleArrayOf(9.0, 10.0, 11.0),
                doubleArrayOf(12.0, 13.0, 14.0)).toMatrix()
        assertEquals(expected, s + A)
        assertEquals(expected, A + s)
    }

    @Test
    fun minusMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()
        val expected1 = arrayOf(
                doubleArrayOf(4.0, 3.0, 2.0),
                doubleArrayOf(1.0, 0.0, -1.0),
                doubleArrayOf(-2.0, -3.0, -4.0)).toMatrix()
        val expected2 = arrayOf(
                doubleArrayOf(-4.0, -3.0, -2.0),
                doubleArrayOf(-1.0, 0.0, 1.0),
                doubleArrayOf(2.0, 3.0, 4.0)).toMatrix()
        assertEquals(expected1, s - A)
        assertEquals(expected2, A - s)
    }

    @Test
    fun divMatrixWithScalar() {
        val s = 5.0
        val A = arrayOf(
                doubleArrayOf(1.0, 2.0, 3.0),
                doubleArrayOf(4.0, 5.0, 6.0),
                doubleArrayOf(7.0, 8.0, 9.0)).toMatrix()
        val expected1 = arrayOf(
                doubleArrayOf(5 / 1.0, 5 / 2.0, 5 / 3.0),
                doubleArrayOf(5 / 4.0, 5 / 5.0, 5 / 6.0),
                doubleArrayOf(5 / 7.0, 5 / 8.0, 5 / 9.0))
        val expected2 = arrayOf(
                doubleArrayOf(0.2, 0.4, 0.6),
                doubleArrayOf(0.8, 1.0, 1.2),
                doubleArrayOf(1.4, 1.6, 1.8))
        assertEquals(expected1, (s / A).matrix, 1e-6)
        assertEquals(expected2, (A / s).matrix, 1e-6)
    }

    @Test
    fun timesMatrixWithMatrix() {
        val A = arrayOf(
                doubleArrayOf(2.0, 5.0),
                doubleArrayOf(0.0, 3.0)).toMatrix()
        val B = arrayOf(
                doubleArrayOf(-3.0, 2.0),
                doubleArrayOf(10.0, 5.0)).toMatrix()
        val expected = arrayOf(
                doubleArrayOf(-6.0, 10.0),
                doubleArrayOf(0.0, 15.0)
        )
        assertEquals(expected, (A * B).matrix, 1e-6)
    }

    @Test
    fun plusMatrixWithMatrix() {
        val A = arrayOf(
                doubleArrayOf(2.0, 5.0),
                doubleArrayOf(0.0, 3.0)).toMatrix()
        val B = arrayOf(
                doubleArrayOf(-3.0, 2.0),
                doubleArrayOf(10.0, 5.0)).toMatrix()
        val expected = arrayOf(
                doubleArrayOf(-1.0, 7.0),
                doubleArrayOf(10.0, 8.0)
        )
        assertEquals(expected, (A + B).matrix, 1e-6)
    }

    @Test
    fun minusMatrixWithMatrix() {
        val A = arrayOf(
                doubleArrayOf(2.0, 5.0),
                doubleArrayOf(0.0, 3.0)).toMatrix()
        val B = arrayOf(
                doubleArrayOf(-3.0, 2.0),
                doubleArrayOf(10.0, 5.0)).toMatrix()
        val expected = arrayOf(
                doubleArrayOf(5.0, 3.0),
                doubleArrayOf(-10.0, -2.0)
        )
        assertEquals(expected, (A - B).matrix, 1e-6)
    }

    @Test
    fun divMatrixWithMatrix() {
        val A = arrayOf(
                doubleArrayOf(2.0, 5.0),
                doubleArrayOf(0.0, 3.0)).toMatrix()
        val B = arrayOf(
                doubleArrayOf(-3.0, 2.0),
                doubleArrayOf(10.0, 5.0)).toMatrix()
        val expected = arrayOf(
                doubleArrayOf(-2 / 3.0, 5 / 2.0),
                doubleArrayOf(0.0, 3 / 5.0)
        )
        assertEquals(expected, (A / B).matrix, 1e-6)
    }

    @Test
    fun basicShermanUpdater() {
        val H = Matrix(6)
        for (i in 0 until 6)
            H[i, i] = 1.0
        val x = Vector(DoubleArray(6) { 0.5 })
        x[2] = 0.0
        val inc = H.shermanUpdater(x)
        val expectedInc = Vector(DoubleArray(6) { 1.0 / 3.0 })
        expectedInc[2] = 0.0
        assertEquals(expectedInc.array, inc.array, 1e-6)
    }

    @Test
    fun fullShermanUpdater() {
        val H = Matrix(arrayOf(
                doubleArrayOf(1.0565, 0.3456, -0.4646, 1.8587),
                doubleArrayOf(0.3456, 0.5910, -0.1395, 0.6277),
                doubleArrayOf(-0.4646, -0.1395, 0.2371, -0.8419),
                doubleArrayOf(1.8587, 0.6277, -0.8419, 4.2134)))
        val x = Vector(doubleArrayOf(1.0, 0.0, 1.0, 1.0)) * sqrt(0.5)

        val inc = H.shermanUpdater(x)

        val expectedInc = doubleArrayOf(0.8351, 0.2841, -0.3644, 1.7823)
        assertEquals(expectedInc, inc.array, 1e-3)
        val H2 = H - (inc outer inc)

        val expected = arrayOf(
                doubleArrayOf(0.3591, 0.1083, -0.1603, 0.3703),
                doubleArrayOf(0.1083, 0.5103, -0.0360, 0.1213),
                doubleArrayOf(-0.1603, -0.0360, 0.1042, -0.1923),
                doubleArrayOf(0.3703, 0.1213, -0.1923, 1.0367))

        assertEquals(expected, H2.matrix, 1e-3)
    }

    @Test
    fun cholDowndate() {
        val H = Matrix(arrayOf(
                doubleArrayOf(1.0565, 0.3456, -0.4646, 1.8587),
                doubleArrayOf(0.3456, 0.5910, -0.1395, 0.6277),
                doubleArrayOf(0.4646, -0.1395, 0.2371, -0.8419),
                doubleArrayOf(.8587, 0.6277, -0.8419, 4.2134)))

        val x = Vector(doubleArrayOf(1.0, 0.0, 1.0, 1.0)) * sqrt(0.5)
        val L = Matrix(arrayOf(
                doubleArrayOf(2.1585, 0.7060, -0.9493, 3.7975),
                doubleArrayOf(0.0, 1.4519, 0.0378, 0.0599),
                doubleArrayOf(0.0, 0.0, 0.3780, -0.2912),
                doubleArrayOf(0.0, 0.0, 0.0, 2.0179)))

        val inc = H.shermanUpdater(x)

        L.cholDowndate(inc)

        // tested using matlabs cholupdate(L,inc,'-')

        val expected = arrayOf(
                doubleArrayOf(1.989, 0.6459, -1.0100, 3.5109),
                doubleArrayOf(0.0, 1.4519, 0.0349, 0.0602),
                doubleArrayOf(0.0, 0.0, 0.1479, -0.6721),
                doubleArrayOf(0.0, 0.0, 0.0, 1.9246))

        assertEquals(expected, L.matrix, 1e-4)
    }
}

