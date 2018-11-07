package combo.util

import combo.math.IntPermutation
import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.*

abstract class IntSetTest {

    abstract fun set(size: Int = 16): IntSet

    @Test
    fun createEmpty() {
        val set = set()
        assertEquals(0, set.size)
        assertTrue { set.isEmpty() }
    }

    @Test
    fun add() {
        val set = set()
        for (i in 0 until 1000) {
            assertEquals(i, set.size)
            set.add(i)
        }
    }

    @Test
    fun addNegative() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = set()
            set.add(-2)
        }
    }

    @Test
    fun containsNotEmpty() {
        val set = set()
        assertFalse(set.contains(0))
    }

    @Test
    fun containsAfterAdd() {
        val set = set()
        set.add(2)
        assertTrue(set.contains(2))
    }

    @Test
    fun addAllIntArray() {
        val set = set()
        set.addAll(intArrayOf(2, 4))
        assertTrue(set.contains(2))
        assertTrue(set.contains(4))
    }

    @Test
    fun addAllIntSequence() {
        val set = set()
        set.addAll((2..4).asSequence().asIterable())
        assertTrue(set.contains(2))
        assertTrue(set.contains(3))
        assertTrue(set.contains(4))
    }

    @Test
    fun removeMissingFromSet() {
        val set = set()
        assertFalse(set.remove(1))
        assertFalse(set.remove(-1))
    }

    @Test
    fun removeFromSetAndAddAgain() {
        val set = set()
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
        val set = set()
        assertTrue { set.toArray().isEmpty() }
    }

    @Test
    fun toArrayOnRemoved() {
        val set = set()
        set.add(0)
        assertEquals(1, set.toArray().size)
        set.remove(0)
        assertEquals(0, set.toArray().size)
    }

    @Test
    fun clear() {
        val set = set()
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
        val set = set()
        assertEquals(0, set.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val set = set()
        set.add(8)
        set.add(1)
        assertEquals(2, set.asSequence().count())
    }

    @Test
    fun randomOnEmpty() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = set()
            set.random()
        }
    }

    @Test
    fun randomOnSingleton() {
        val set = set()
        set.add(12300)
        assertEquals(12300, set.random())
    }

    @Test
    fun multipleRehash() {
        val set = set(2)
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
        val r = Random(0)
        val all = ArrayList<Int>()
        val set = set()
        val test = HashSet<Int>()
        for (i in 1..1_000) {
            val n = r.nextInt(Int.MAX_VALUE)
            all.add(n)
            assertEquals(test.add(n), set.add(n))
            assertEquals(test.add(n), set.add(n))
            if (r.nextBoolean()) {
                val remove = all[r.nextInt(all.size)]
                assertEquals(test.remove(remove), set.remove(remove))
                assertEquals(test.remove(remove), set.remove(remove))
            }
        }
        for (i in all)
            assertEquals(test.remove(i), set.remove(i))
    }
}

class ArrayIntSetTest : IntSetTest() {
    override fun set(size: Int) = ArrayIntSet(size)
}

class SortedArrayIntSetTest : IntSetTest() {
    override fun set(size: Int) = SortedArrayIntSet(size)

    @Test
    fun ordering() {
        val p = IntPermutation(20)
        val s = set()
        s.addAll(p)
        assertContentEquals(s.toArray(), (0..19).toList().toIntArray())
    }
}
