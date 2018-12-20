package combo.util

import kotlin.random.Random
import kotlin.test.*

class IntListTest {

    @Test
    fun createEmpty() {
        val list = IntList()
        assertEquals(0, list.size)
        assertTrue { list.isEmpty() }
    }

    @Test
    fun add() {
        val list = IntList()
        for (i in 0 until 1000) {
            assertEquals(i, list.size)
            list.add(i)
        }
    }

    @Test
    fun containsNotEmpty() {
        val list = IntList()
        assertFalse(list.contains(0))
    }

    @Test
    fun containsAfterAdd() {
        val list = IntList()
        list.add(2)
        assertTrue(list.contains(2))
    }

    @Test
    fun addAllIntArray() {
        val list = IntList()
        list.addAll(intArrayOf(2, 4))
        assertTrue(list.contains(2))
        assertTrue(list.contains(4))
    }

    @Test
    fun addAllIterable() {
        val list = IntList()
        assertTrue(list.addAll((2..4).asIterable()))
        assertTrue(list.contains(2))
        assertTrue(list.contains(3))
        assertTrue(list.contains(4))
    }

    @Test
    fun removeMissingFromSet() {
        val list = IntList()
        assertFalse(list.remove(1))
        assertFalse(list.remove(-1))
    }

    @Test
    fun removeFromSetAndAddAgain() {
        val list = IntList()
        list.add(2)
        list.add(8)
        assertFalse(list.remove(3))
        assertEquals(2, list.size)
        assertTrue(list.remove(2))
        assertEquals(1, list.size)
        assertTrue(list.add(2))
        assertEquals(2, list.size)
    }

    @Test
    fun toArrayOnEmpty() {
        val list = IntList()
        assertTrue { list.toArray().isEmpty() }
    }

    @Test
    fun toArrayOnRemoved() {
        val list = IntList()
        list.add(0)
        assertEquals(1, list.toArray().size)
        list.remove(0)
        assertEquals(0, list.toArray().size)
    }

    @Test
    fun clear() {
        val list = IntList()
        for (i in 4..10)
            list.add(i)
        list.remove(5)
        list.clear()
        assertEquals(0, list.size)
        list.add(4)
        assertEquals(1, list.size)
    }

    @Test
    fun emptySequence() {
        val list = IntList()
        assertEquals(0, list.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val list = IntList()
        list.add(8)
        list.add(1)
        assertEquals(2, list.asSequence().count())
    }

    @Test
    fun randomOnEmpty() {
        assertFailsWith(IllegalArgumentException::class) {
            val list = IntList()
            list.random()
        }
    }

    @Test
    fun randomOnSingleton() {
        val list = IntList()
        list.add(12300)
        assertEquals(12300, list.random())
    }

    @Test
    fun multipleRehash() {
        val list = IntList(2)
        list.addAll((1..100).asSequence().asIterable())
        list.clear()
        list.addAll((1100..1120).asSequence().asIterable())
        list.clear()
        list.addAll((200..300).asSequence().asIterable())
        assertEquals(101, list.size)
        assertFalse(list.contains(1))
        assertTrue(list.contains(200))
    }

    @Test
    fun largeRandomTest() {
        val r = Random(0)
        val all = ArrayList<Int>()
        val list = IntList()
        val test = HashSet<Int>()
        for (i in 1..1_000) {
            val n = r.nextInt(Int.MAX_VALUE)
            all.add(n)
            assertEquals(test.add(n), list.add(n))
            if (r.nextBoolean()) {
                val remove = all[r.nextInt(all.size)]
                assertEquals(test.remove(remove), list.remove(remove))
                assertEquals(test.remove(remove), list.remove(remove))
            }
        }
        for (i in all)
            assertEquals(test.remove(i), list.remove(i))
    }

    @Test
    fun iterator() {
        val s = IntList()
        s.addAll(generateSequence { Random.nextInt(0, Int.MAX_VALUE / 2) }.take(10).asIterable())
        assertTrue(s.iterator().hasNext())
        assertEquals(10, s.iterator().asSequence().toSet().size)
        assertEquals(10, s.iterator().asSequence().toSet().size)
    }

    @Test
    fun copySame() {
        val s1 = IntList()
        s1.addAll(generateSequence { Random.nextInt(0, Int.MAX_VALUE / 2) }.take(10).asIterable())
        val s2 = s1.copy()
        assertEquals(s1.size, s2.size)
        for (i in s1) {
            assertTrue(i in s2)
        }
    }
}

class IntSetTest {

    @Test
    fun createEmpty() {
        val set = IntSet()
        assertEquals(0, set.size)
        assertTrue { set.isEmpty() }
    }

    @Test
    fun add() {
        val set = IntSet()
        for (i in 0 until 1000) {
            assertEquals(i, set.size)
            set.add(i)
        }
    }

    @Test
    fun addDuplicate() {
        val set = IntSet()
        for (i in 0 until 1000) {
            assertEquals(i, set.size)
            assertTrue(set.add(i))
            assertFalse(set.add(i))
        }
    }

    @Test
    fun addNegative() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = IntSet()
            set.add(-2)
        }
    }

    @Test
    fun containsNotEmpty() {
        val set = IntSet()
        assertFalse(set.contains(0))
    }

    @Test
    fun containsAfterAdd() {
        val set = IntSet()
        set.add(2)
        assertTrue(set.contains(2))
    }

    @Test
    fun addAllIntArray() {
        val set = IntSet()
        set.addAll(intArrayOf(2, 4))
        assertTrue(set.contains(2))
        assertTrue(set.contains(4))
    }

    @Test
    fun addAllIterable() {
        val set = IntSet()
        assertTrue(set.addAll((2..4).asIterable()))
        assertFalse(set.addAll((2..4).asIterable()))
        assertTrue(set.contains(2))
        assertTrue(set.contains(3))
        assertTrue(set.contains(4))
    }

    @Test
    fun removeMissingFromSet() {
        val set = IntSet()
        assertFalse(set.remove(1))
        assertFalse(set.remove(-1))
    }

    @Test
    fun removeFromSetAndAddAgain() {
        val set = IntSet()
        set.add(2)
        set.add(8)
        assertFalse(set.remove(3))
        assertEquals(2, set.size)
        assertTrue(set.remove(2))
        assertEquals(1, set.size)
        assertTrue(set.add(2))
        assertEquals(2, set.size)
    }

    @Test
    fun toArrayOnEmpty() {
        val set = IntSet()
        assertTrue { set.toArray().isEmpty() }
    }

    @Test
    fun toArrayOnRemoved() {
        val set = IntSet()
        set.add(0)
        assertEquals(1, set.toArray().size)
        set.remove(0)
        assertEquals(0, set.toArray().size)
    }

    @Test
    fun clear() {
        val set = IntSet()
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
        val set = IntSet()
        assertEquals(0, set.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val set = IntSet()
        set.add(8)
        set.add(1)
        assertEquals(2, set.asSequence().count())
    }

    @Test
    fun randomOnEmpty() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = IntSet()
            set.random()
        }
    }

    @Test
    fun randomOnSingleton() {
        val set = IntSet()
        set.add(12300)
        assertEquals(12300, set.random())
    }

    @Test
    fun multipleRehash() {
        val set = IntSet(2)
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
        val set = IntSet()
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

    @Test
    fun iterator() {
        val s = IntSet()
        s.addAll(generateSequence { Random.nextInt(0, Int.MAX_VALUE / 2) }.take(10).asIterable())
        assertTrue(s.iterator().hasNext())
        assertEquals(10, s.iterator().asSequence().toSet().size)
        assertEquals(10, s.iterator().asSequence().toSet().size)
    }

    @Test
    fun copySame() {
        val s1 = IntSet()
        s1.addAll(generateSequence { Random.nextInt(0, Int.MAX_VALUE / 2) }.take(10).asIterable())
        val s2 = s1.copy()
        assertEquals(s1.size, s2.size)
        for (i in s1) {
            assertTrue(i in s2)
        }
    }
}
