package combo.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArrayQueueTest {
    @Test
    fun addRemove() {
        val aq = ArrayQueue<Int>()
        aq.add(1)
        aq.add(2)
        aq.add(3)
        assertEquals(1, aq.poll())
        assertEquals(2, aq.poll())
        assertEquals(3, aq.poll())
        assertNull(aq.poll())
    }

    @Test
    fun addRemoveLots() {
        val aq = ArrayQueue<Int>()
        for (i in 0 until 10)
            aq.add(i)
        for (i in 0 until 10)
            assertEquals(i, aq.poll())

        aq.add(100)
        aq.add(200)
        assertEquals(100, aq.poll())
        aq.add(300)
        aq.add(400)
        assertEquals(200, aq.poll())
    }

    @Test
    fun pollEmpty() {
        val aq = ArrayQueue<String>()
        assertNull(aq.poll())
    }

    @Test
    fun addPollPollEmpty() {
        val aq = ArrayQueue<Int>()
        aq.add(1)
        aq.poll()
        assertNull(aq.poll())
        assertNull(aq.peek())
    }
}