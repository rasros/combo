package combo.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicLongTest {
    @Test
    fun getAndIncrement() {
        val l = AtomicLong(0)
        assertEquals(0L, l.inc())
        assertEquals(1L, l.inc())
        assertEquals(2L, l.get())
    }

    @Test
    fun get() {
        val l = AtomicLong(20)
        assertEquals(20, l.get())
    }
}

class AtomicIntTest {
    @Test
    fun getAndIncrement() {
        val l = AtomicInt(0)
        assertEquals(0, l.inc())
        assertEquals(1, l.inc())
        assertEquals(2, l.get())
    }

    @Test
    fun get() {
        val l = AtomicInt(20)
        assertEquals(20, l.get())
    }
}
