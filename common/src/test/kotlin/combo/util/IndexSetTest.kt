package combo.util

import combo.math.Rng
import kotlin.test.*

class IndexSetTest {

    @Test
    fun createEmpty() {
        val set = IndexSet()
        assertEquals(0, set.size)
        assertTrue { set.isEmpty() }
    }

    @Test
    fun add() {
        val set = IndexSet()
        for (i in 0 until 1000) {
            assertEquals(i, set.size)
            set.add(i)
        }
    }

    @Test
    fun addNegative() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = IndexSet()
            set.add(-2)
        }
    }

    @Test
    fun containsNotEmpty() {
        val set = IndexSet()
        assertFalse(set.contains(0))
    }

    @Test
    fun containsAfterAdd() {
        val set = IndexSet()
        set.add(2)
        assertTrue(set.contains(2))
    }

    @Test
    fun addAllIntArray() {
        val set = IndexSet()
        set.addAll(intArrayOf(2, 4))
        assertTrue(set.contains(2))
        assertTrue(set.contains(4))
    }

    @Test
    fun addAllIntSequence() {
        val set = IndexSet()
        set.addAll((2..4).asSequence().asIterable())
        assertTrue(set.contains(2))
        assertTrue(set.contains(3))
        assertTrue(set.contains(4))
    }

    @Test
    fun removeMissingFromSet() {
        val set = IndexSet()
        assertFalse(set.remove(1))
        assertFalse(set.remove(-1))
    }

    @Test
    fun removeFromSetAndAddAgain() {
        val set = IndexSet()
        set.add(2)
        set.add(8)
        assertFalse(set.remove(3))
        assertEquals(2, set.size)
        assertTrue(set.remove(2))
        assertEquals(1, set.size)
        set.add(2)
        assertEquals(2, set.size)
    }

    @Test
    fun toArrayOnEmpty() {
        val set = IndexSet()
        assertTrue { set.toArray().isEmpty() }
    }

    @Test
    fun toArrayOnRemoved() {
        val set = IndexSet()
        set.add(0)
        assertEquals(1, set.toArray().size)
        set.remove(0)
        assertEquals(0, set.toArray().size)
    }

    @Test
    fun clear() {
        val set = IndexSet()
        for (i in 4..10)
            set.add(i)
        set.remove(5)
        set.clear()
        assertEquals(0, set.size)
        set.add(4)
        assertEquals(1, set.size)
    }

    @Test
    fun emptySequence() {
        val set = IndexSet()
        assertEquals(0, set.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val set = IndexSet()
        set.add(8)
        set.add(1)
        assertEquals(2, set.asSequence().count())
    }

    @Test
    fun randomOnEmpty() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = IndexSet()
            set.random()
        }
    }

    @Test
    fun randomOnSingleton() {
        val set = IndexSet()
        set.add(12300)
        assertEquals(12300, set.random())
    }

    @Test
    fun multipleRehash() {
        val set = IndexSet(2)
        set.addAll((1..100).asSequence().asIterable())
        set.clear()
        set.addAll((1100..1120).asSequence().asIterable())
        set.clear()
        set.addAll((200..300).asSequence().asIterable())
        assertEquals(101, set.size)
        assertFalse(set.contains(1))
        assertTrue(set.contains(200))
    }

    @Test
    fun largeRandomTest() {
        val r = Rng(0)
        val all = ArrayList<Int>()
        val set = IndexSet()
        val test = HashSet<Int>()
        for (i in 1..1_000) {
            val n = r.int()
            all.add(n)
            assertEquals(test.add(n), set.add(n), r.seed.toString())
            assertEquals(test.add(n), set.add(n), r.seed.toString())
            if (r.boolean()) {
                val remove = all[r.int(all.size)]
                assertEquals(test.remove(remove), set.remove(remove), r.seed.toString())
                assertEquals(test.remove(remove), set.remove(remove), r.seed.toString())
            }
        }
        for (i in all)
            assertEquals(test.remove(i), set.remove(i), r.seed.toString())
    }
}
