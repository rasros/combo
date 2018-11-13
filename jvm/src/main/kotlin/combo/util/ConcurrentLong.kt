@file:JvmName("ConcurrentLong")

package combo.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

actual class ConcurrentLong actual constructor(value: Long) {
    private val value: AtomicLong = AtomicLong(value)
    actual fun getAndIncrement() = value.getAndIncrement()
    actual fun compareAndSet(expect: Long, update: Long) = value.compareAndSet(expect, update)
    actual fun get() = value.get()
}

actual class ConcurrentInteger actual constructor(value: Int) {
    private val value: AtomicInteger = AtomicInteger(value)
    actual fun getAndIncrement() = value.getAndIncrement()
    actual fun compareAndSet(expect: Int, update: Int) = value.compareAndSet(expect, update)
    actual fun get() = value.get()
}
