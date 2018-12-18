package combo.sat.solvers

import combo.sat.ByteArrayLabeling
import combo.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectiveFunctionTest {
    @Test
    fun scoreMaximize() {
        val weights = DoubleArray(8) { 1.0 }
        val function = LinearObjective(weights)
        val upperBound = function.upperBound(true)
        val lowerBound = function.lowerBound(true)

        val ones = ByteArrayLabeling(ByteArray(8) { 1 })
        val zeros = ByteArrayLabeling(8)

        assertEquals(8.0, function.value(ones, 0, lowerBound, upperBound, true))
        assertTrue(function.value(ones, 1, lowerBound, upperBound, true) < lowerBound)
        assertEquals(function.value(zeros, 0, lowerBound, upperBound, true), lowerBound)
    }

    @Test
    fun scoreMinimize() {
        val weights = DoubleArray(8) { 1.0 }
        val function = LinearObjective(weights)
        val upperBound = function.upperBound(false)
        val lowerBound = function.lowerBound(false)

        val ones = ByteArrayLabeling(ByteArray(8) { 1 })
        val zeros = ByteArrayLabeling(8)

        assertEquals(0.0, function.value(zeros, 0, lowerBound, upperBound, false), 0.0)
        assertTrue(function.value(ones, 1, lowerBound, upperBound, false) < lowerBound)
        assertEquals(function.value(ones, 0, lowerBound, upperBound, false), lowerBound)
    }
}

class LinearObjectiveTest {
    @Test
    fun bounds() {
        val function = LinearObjective(doubleArrayOf(-1.0, 2.0, 0.0, -3.0, 3.0))

        assertEquals(5.0, function.upperBound(true))
        assertEquals(-4.0, function.lowerBound(true))

        assertEquals(4.0, function.upperBound(false))
        assertEquals(-5.0, function.lowerBound(false))
    }

    @Test
    fun value() {
        val function = LinearObjective(doubleArrayOf(-1.0, 2.0, 0.0, -3.0, 3.0))
        assertEquals(0.0, function.value(ByteArrayLabeling(5)))
        assertEquals(1.0, function.value(ByteArrayLabeling(ByteArray(5) { 1 })))
    }
}
