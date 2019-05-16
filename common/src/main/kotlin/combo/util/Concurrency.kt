package combo.util

import kotlin.random.Random

expect class AtomicLong(value: Long = 0L) {
    fun inc(): Long
    operator fun plus(value: Long): Long
    fun get(): Long
}

expect class AtomicInt(value: Int = 0) {
    fun inc(): Int
    operator fun plus(value: Int): Int
    fun get(): Int
}

expect class RandomConcurrentBuffer<E>(maxSize: Int) {
    fun get(rng: Random, filter: (E) -> Boolean, create: () -> E): E
    fun add(rng: Random, e: E)
    fun find(predicate: (E) -> Boolean): E?
    fun forEach(action: (E) -> Unit)
}
