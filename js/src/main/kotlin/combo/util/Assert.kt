package combo.util

@Suppress("NOTHING_TO_INLINE")
actual inline fun assert(value: Boolean) {
    if (!value) throw AssertionError()
}

actual inline fun assert(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw AssertionError(lazyMessage.invoke())
}