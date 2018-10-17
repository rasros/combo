package combo.util

import kotlin.js.Date

actual fun nanos() = Date().getTime().toLong() * 1000L
actual fun millis() = Date().getTime().toLong()
