package combo.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BitsTest {
    @Test
    fun msb() {
        assertEquals(0, Int.msb(0))
        assertEquals(0, Int.msb(1))
        assertEquals(1, Int.msb(2))
        assertEquals(1, Int.msb(3))
        assertEquals(3, Int.msb(10))
        assertEquals(5, Int.msb(32))
        assertEquals(9, Int.msb(1023))
        assertEquals(10, Int.msb(1024))
        assertEquals(30, Int.msb(Int.MAX_VALUE))
        assertEquals(31, Int.msb(Int.MIN_VALUE))
        assertEquals(31, Int.msb(-1))
        assertEquals(31, Int.msb(-1241))
        assertEquals(31, Int.msb(-104192))
    }

    @Test
    fun lsb() {
        assertEquals(0, Int.lsb(0))
        assertEquals(0, Int.lsb(1))
        assertEquals(1, Int.lsb(2))
        assertEquals(0, Int.lsb(3))
        assertEquals(1, Int.lsb(10))
        assertEquals(5, Int.lsb(32))
        assertEquals(0, Int.lsb(1023))
        assertEquals(10, Int.lsb(1024))
        assertEquals(0, Int.lsb(1025))
        assertEquals(0, Int.lsb(Int.MAX_VALUE))
        assertEquals(31, Int.lsb(Int.MIN_VALUE))
        assertEquals(0, Int.lsb(-1))
        assertEquals(3, Int.lsb(-1240))
        assertEquals(8, Int.lsb(-104192))
    }

    @Test
    fun floatMinValue() {
        val f1 = MIN_VALUE32
        val i = f1.toBits()
        val f2 = Float.fromBits(i)
        assertEquals(f1.toBits(), f2.toBits())
    }
}
