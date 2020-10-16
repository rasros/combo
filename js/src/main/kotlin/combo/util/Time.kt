@file:Suppress("NOTHING_TO_INLINE")

package combo.util

import kotlin.js.Date

actual inline fun nanos() = Date().getTime().toLong()
actual inline fun millis() = Date().getTime().toLong()
