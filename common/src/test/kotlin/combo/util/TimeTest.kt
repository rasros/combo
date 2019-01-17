package combo.util

import kotlin.test.Test
import kotlin.test.assertTrue

class TimeTest {
    @Test
    fun millisMin() {
        assertTrue(millis() > 1528881361103L)
    }

    @Test
    fun nanosGreaterThanMillis() {
        val nowMillis = millis()
        val nowNanos = nanos()
        val thenMillis = millis()
        val thenNanos = nanos()
        assertTrue(thenNanos - nowNanos >= thenMillis - nowMillis)
    }
}
