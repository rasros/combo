package combo.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BitsTest {
    @Test
    fun floatMinValue() {
        val f1 = MIN_VALUE32
        val i = f1.toBits()
        val f2 = Float.fromBits(i)
        assertEquals(f1.toBits(), f2.toBits())
    }
}