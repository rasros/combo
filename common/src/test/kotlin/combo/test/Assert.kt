package combo.test

import kotlin.math.abs
import kotlin.test.assertTrue

fun assertEquals(expected: Double, actual: Double, epsilon: Double, message: String? = null) {
    if (actual == expected) return // prevents NaN/Inf. errors
    val abs = abs(expected - actual)
    assertTrue(abs <= epsilon, (message ?: "") + "\n" +
            "Expected :$expected\n" +
            "Actual   :$actual\n" +
            "Err      :$abs > $epsilon")
}

fun assertContentEquals(expected: DoubleArray, actual: DoubleArray, epsilon: Double = 0.0) {
    for (i in actual.indices)
        assertEquals(expected[i], actual[i], epsilon)
}

fun assertContentEquals(expected: Array<DoubleArray>, actual: Array<DoubleArray>, epsilon: Double = 0.0) {
    for (i in actual.indices)
        for (j in actual[i].indices)
            assertEquals(expected[i][j], actual[i][j], epsilon)
}

fun assertContentEquals(expected: Array<*>, actual: Array<*>, message: String? = null) {
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
