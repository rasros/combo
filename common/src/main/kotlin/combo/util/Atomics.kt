package combo.util

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


