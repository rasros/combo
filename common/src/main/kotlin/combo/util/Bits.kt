@file:JvmName("Bits")

package combo.util

import kotlin.jvm.JvmName
import kotlin.math.absoluteValue

fun Int.Companion.power2(value: Int): Int {
    var x = value - 1
    x = x or (x shr 1)
    x = x or (x shr 2)
    x = x or (x shr 4)
    x = x or (x shr 8)
    x = x or (x shr 16)
    return x + 1
}

fun Int.Companion.msb(value: Int): Int {
    assert(value >= 0)
    var v = value
    var r = 0

    while (v != 0) {
        v = v shr 1
        r++
    }
    return r
}

fun Int.Companion.bitSize(value: Int): Int {
    return if (value < 0) Int.msb(value.absoluteValue - 1)
    else Int.msb(value)
}

/**
 * This is adapted to kotlin from the JDK java.lang.Integer.bitCount method.
 */
fun Int.Companion.bitCount(value: Int): Int {
    var i = value
    i = i - ((i ushr 1) and 0x55555555)
    i = (i and 0x33333333) + ((i ushr 2) and 0x33333333)
    i = (i + (i ushr 4)) and 0x0F0F0F0F
    i = i + (i ushr 8)
    i = i + (i ushr 16)
    return i and 0x3F
}

/**
 * Maximum value of Float in binary32 format. This ensures that the max value is same in all platforms.
 */
val MAX_VALUE32 = 3.4028235e+38f
val MIN_VALUE32 = 1.4e-45f