package combo.util

import kotlin.math.exp

actual class AtomicLong actual constructor(private var value: Long) {

    actual fun get() = value
    actual fun getAndIncrement(): Long {
        return value++
    }

    actual fun set(value: Long) {
        this.value = value
    }

    actual fun getAndDecrement(): Long {
        return value--
    }

    actual fun compareAndSet(expect: Long, update: Long): Boolean {
        return if (expect == value) {
            value = update
            true
        } else false
    }
}

actual class AtomicInt actual constructor(private var value: Int) {

    actual fun get() = value
    actual fun getAndIncrement(): Int {
        return value++
    }

    actual fun set(value: Int) {
        this.value = value
    }

    actual fun getAndDecrement(): Int {
        return value--
    }

    actual fun compareAndSet(expect: Int, update: Int): Boolean {
        return if (expect == value) {
            value = update
            true
        } else false
    }
}

actual class AtomicReference<V> actual constructor(private var value: V) {
    actual fun compareAndSet(expect: V, update: V): Boolean {
        return if (value === expect) {
            value = update
            true
        } else false
    }

    actual fun getAndSet(newValue: V) = value.also { value = newValue }
    actual fun get() = value
}