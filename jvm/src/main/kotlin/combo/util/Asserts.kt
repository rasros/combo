@file:JvmName("Asserts")

package combo.util

@Suppress("NOTHING_TO_INLINE")
actual inline fun assert(value: Boolean) {
    kotlin.assert(value)
}
