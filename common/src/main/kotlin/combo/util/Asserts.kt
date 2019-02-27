package combo.util

expect inline fun assert(value: Boolean)
expect inline fun assert(value: Boolean, lazyMessage: () -> Any)
