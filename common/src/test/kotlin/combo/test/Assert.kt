package combo.test

import kotlin.math.abs
import kotlin.test.assertTrue

fun assertEquals(expected: Double, actual: Double, epsilon: Double) {
    val abs = abs(expected - actual)
    assertTrue(abs < epsilon, "Expected delta to be less than eps=$epsilon " +
            "but was err=$abs for actual=$actual and expected=$expected.")
}

fun assertEquals(expected: DoubleArray, actual: DoubleArray, epsilon: Double) {
    for (i in actual.indices)
        assertEquals(expected[i], actual[i], epsilon)
}

fun assertEquals(expected: Array<DoubleArray>, actual: Array<DoubleArray>, epsilon: Double) {
    for (i in actual.indices)
        for (j in actual[i].indices)
            assertEquals(expected[i][j], actual[i][j], epsilon)
}

fun assertContentEquals(expected: Array<*>, actual: Array<*>, message: String? = null) {
    assertTrue(expected.contentEquals(actual), message
            ?: "Expected ${actual.joinToString(",", "<", ">")} to equal ${expected.joinToString(",", "<", ">")}")
}

fun assertContentEquals(expected: DoubleArray, actual: DoubleArray, message: String? = null) {
    assertTrue(expected.contentEquals(actual), message
            ?: "Expected ${actual.joinToString(",", "<", ">")} to equal ${expected.joinToString(",", "<", ">")}")
}

fun assertContentEquals(expected: IntArray, actual: IntArray, message: String? = null) {
    assertTrue(expected.contentEquals(actual), message
            ?: "Expected ${actual.joinToString(",", "<", ">")} to equal ${expected.joinToString(",", "<", ">")}")
}

fun assertContentEquals(expected: LongArray, actual: LongArray, message: String? = null) {
    assertTrue(expected.contentEquals(actual), message
            ?: "Expected ${actual.joinToString(",", "<", ">")} to equal ${expected.joinToString(",", "<", ">")}")
}

fun assertContentEquals(expected: List<*>, actual: List<*>, message: String? = null) {
    assertTrue(expected.toTypedArray().contentEquals(actual.toTypedArray()), message
            ?: "Expected ${actual.joinToString(",", "<", ">")} to equal ${expected.joinToString(",", "<", ">")}")
}
