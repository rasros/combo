package combo.util

expect class AtomicLong(value: Long = 0L) {
    fun getAndIncrement(): Long
    fun getAndDecrement(): Long
    fun get(): Long
    fun set(value: Long)
}

expect class AtomicInt(value: Int = 0) {
    fun getAndIncrement(): Int
    fun getAndDecrement(): Int
    fun get(): Int
    fun set(value: Int)
}

expect class AtomicBool(value: Boolean = false) {
    fun get(): Boolean
    fun set(value: Boolean)
}

expect class AtomicReference<V>(value: V) {
    fun compareAndSet(expect: V, update: V): Boolean
    fun getAndSet(newValue: V): V
    fun get(): V
}