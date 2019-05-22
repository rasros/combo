package combo.util

import java.util.concurrent.atomic.AtomicInteger as JavaAtomicInt
import java.util.concurrent.atomic.AtomicLong as JavaAtomicLong

actual class AtomicLong actual constructor(value: Long) {
    private val value: JavaAtomicLong = JavaAtomicLong(value)
    actual fun inc() = value.getAndIncrement()
    actual operator fun plus(value: Long) = this.value.addAndGet(value)
    actual fun get() = value.get()
}

actual class AtomicInt actual constructor(value: Int) {
    private val value: JavaAtomicInt = JavaAtomicInt(value)
    actual fun inc() = value.getAndIncrement()
    actual operator fun plus(value: Int) = this.value.addAndGet(value)
    actual fun get() = value.get()
}