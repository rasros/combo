package combo.util

expect class AtomicLong(value: Long = 0L) {
    fun getAndIncrement(): Long
    fun getAndDecrement(): Long
    fun compareAndSet(expect: Long, update: Long): Boolean
    fun get(): Long
    fun set(value: Long)
}

expect class AtomicInt(value: Int = 0) {
    fun getAndIncrement(): Int
    fun getAndDecrement(): Int
    fun compareAndSet(expect: Int, update: Int): Boolean
    fun get(): Int
    fun set(value: Int)
}

expect class AtomicReference<V>(value: V) {
    fun compareAndSet(expect: V, update: V): Boolean
    fun getAndSet(newValue: V): V
    fun get(): V
}