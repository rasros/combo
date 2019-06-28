package combo.util

@Suppress("NOTHING_TO_INLINE")
actual inline fun assert(value: Boolean) {
    kotlin.assert(value)
}

actual inline fun assert(value: Boolean, lazyMessage: () -> String) {
    kotlin.assert(value, lazyMessage)
}