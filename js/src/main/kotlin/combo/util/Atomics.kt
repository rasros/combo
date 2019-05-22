package combo.util

import combo.math.IntPermutation
import kotlin.random.Random

actual class AtomicLong actual constructor(private var value: Long) {

    actual fun inc(): Long {
        return value++
    }

    actual operator fun plus(value: Long): Long {
        this.value += value
        return this.value
    }

    actual fun get() = value
}

actual class AtomicInt actual constructor(private var value: Int) {

    actual fun inc(): Int {
        return value++
    }

    actual operator fun plus(value: Int): Int {
        this.value += value
        return this.value
    }

    actual fun get() = value
}

