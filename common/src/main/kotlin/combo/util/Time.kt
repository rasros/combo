package combo.util

expect fun nanos(): Long
expect fun millis(): Long

inline fun measureTimeMillis(block: () -> Unit): Long {
    val start = millis()
    block()
    return millis() - start
}

inline fun measureTimeNanos(block: () -> Unit): Long {
    val start = nanos()
    block()
    return nanos() - start
}
