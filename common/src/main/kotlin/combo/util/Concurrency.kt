package combo.util

import kotlin.random.Random

expect class AtomicLong(value: Long = 0L) {
    fun compareAndSet(expect: Long, update: Long): Boolean
    fun getAndIncrement(): Long
    fun get(): Long
}

expect class AtomicInt(value: Int = 0) {
    fun compareAndSet(expect: Int, update: Int): Boolean
    fun getAndIncrement(): Int
    fun get(): Int
}

expect class ConcurrentCache<E>(maxSize: Int) {
    fun get(rng: Random, filter: (E) -> Boolean, create: () -> E): E
    fun add(rng: Random, e: E)
    fun forEach(action: (E) -> Unit)
}