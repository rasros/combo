package combo.util

fun Int.Companion.msb(value: Int): Int {
    var x = value
    x = x or (x shr 1)
    x = x or (x shr 2)
    x = x or (x shr 4)
    x = x or (x shr 8)
    x = x or (x shr 16)
    x = x or (x shr 24)
    return x - (x shr 1)
}

/**
 * This is adapted to kotlin from the JDK java.lang.Integer.bitCount method.
 */
fun Int.Companion.bitCount(value: Int): Int {
    var i = value
    i = i - ((i ushr 1) and 0x55555555)
    i = (i and 0x33333333) + ((i ushr 2) and 0x33333333)
    i = (i + (i ushr 4)) and 0x0f0f0f0f
    i = i + (i ushr 8)
    i = i + (i ushr 16)
    return i and 0x3f
}