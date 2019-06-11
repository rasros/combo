@file:JvmName("Time")
@file:Suppress("NOTHING_TO_INLINE")

package combo.util

actual inline fun nanos() = System.nanoTime()
actual inline fun millis() = System.currentTimeMillis()
