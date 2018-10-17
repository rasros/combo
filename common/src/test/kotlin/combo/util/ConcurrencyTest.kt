package combo.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcurrencyLongTest {
    @Test
    fun getAndIncrement() {
        val l = ConcurrentLong(0)
        assertEquals(0L, l.getAndIncrement())
        assertEquals(1L, l.getAndIncrement())
        assertEquals(2L, l.get())
    }

    @Test
    fun get() {
        val l = ConcurrentLong(20)
        assertEquals(20, l.get())
    }

    @Test
    fun compareAndSetNotChanging() {
        val l = ConcurrentLong(10)
        assertFalse(l.compareAndSet(11, 12))
        assertEquals(10, l.get())
    }

    @Test
    fun compareAndSetChanging() {
        val l = ConcurrentLong(10)
        assertTrue(l.compareAndSet(10, 11))
        assertEquals(11, l.get())
    }
}

class ConcurrencyIntegerTest {
    @Test
    fun getAndIncrement() {
        val l = ConcurrentInteger(0)
        assertEquals(0, l.getAndIncrement())
        assertEquals(1, l.getAndIncrement())
        assertEquals(2, l.get())
    }

    @Test
    fun get() {
        val l = ConcurrentInteger(20)
        assertEquals(20, l.get())
    }

    @Test
    fun compareAndSetNotChanging() {
        val l = ConcurrentInteger(10)
        assertFalse(l.compareAndSet(11, 12))
        assertEquals(10, l.get())
    }

    @Test
    fun compareAndSetChanging() {
        val l = ConcurrentInteger(10)
        assertTrue(l.compareAndSet(10, 11))
        assertEquals(11, l.get())
    }
}
