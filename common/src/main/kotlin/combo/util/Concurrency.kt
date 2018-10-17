package combo.util

expect class ConcurrentLong(value: Long = 0L) {
    fun compareAndSet(expect: Long, update: Long): Boolean
    fun getAndIncrement(): Long
    fun get(): Long
}

expect class ConcurrentInteger(value: Int = 0) {
    fun compareAndSet(expect: Int, update: Int): Boolean
    fun getAndIncrement(): Int
    fun get(): Int
}
