package combo.util

expect fun nanos(): Long
expect fun millis(): Long

inline fun measureTimeNanos(block: () -> Unit): Long {
    val start = nanos()
    block()
    return nanos() - start
}
