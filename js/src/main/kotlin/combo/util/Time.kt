package combo.util

import kotlin.js.Date

actual fun nanos() = Date().getTime().toLong()
actual fun millis() = Date().getTime().toLong()
