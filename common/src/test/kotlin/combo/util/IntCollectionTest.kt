package combo.util

import combo.test.assertContentEquals
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
            list.random(Random)
        }
    }

    @Test
    fun randomOnSingleton() {
        val list = IntList()
        list.add(12300)
        assertEquals(12300, list.random(Random))
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

    @Test
    fun permutation() {
        val s1 = IntList()
        s1.addAll(10..20)
        val s2 = s1.permutation(Random).asSequence().toSet()
        assertContentEquals(s1.toArray().also { it.sort() }, s2.toIntArray().also { it.sort() })
    }
}

class IntHashSetTest {

    @Test
    fun createEmpty() {
        val set = IntHashSet()
        assertEquals(0, set.size)
        assertTrue { set.isEmpty() }
    }

    @Test
    fun add() {
        val set = IntHashSet(nullValue = -1)
        for (i in 0 until 1000) {
            assertEquals(i, set.size)
            set.add(i)
        }
    }

    @Test
    fun addDuplicate() {
        val set = IntHashSet(nullValue = -1)
        for (i in 0 until 1000) {
            assertEquals(i, set.size)
            assertTrue(set.add(i))
            assertFalse(set.add(i))
        }
    }

    @Test
    fun addNullValue() {
        assertFailsWith(IllegalArgumentException::class) {
            val set = IntHashSet(nullValue = 0)
            set.add(0)
        }
    }

    @Test
    fun containsNotEmpty() {
        val set = IntHashSet()
        assertFalse(set.contains(0))
    }

    @Test
    fun containsAfterAdd() {
        val set = IntHashSet()
        set.add(2)
        assertTrue(set.contains(2))
    }

    @Test
    fun addAllIntArray() {
        val set = IntHashSet()
        set.addAll(intArrayOf(2, 4))
        assertTrue(set.contains(2))
        assertTrue(set.contains(4))
    }

    @Test
    fun addAllIterable() {
        val set = IntHashSet()
        assertTrue(set.addAll((2..4).asIterable()))
        assertFalse(set.addAll((2..4).asIterable()))
        assertTrue(set.contains(2))
        assertTrue(set.contains(3))
        assertTrue(set.contains(4))
    }

    @Test
    fun removeMissingFromSet() {
        val set = IntHashSet()
        assertFalse(set.remove(1))
        assertFalse(set.remove(-1))
    }

    @Test
    fun removeFromSetAndAddAgain() {
        val set = IntHashSet()
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
        val set = IntHashSet()
        assertTrue { set.toArray().isEmpty() }
    }

    @Test
    fun toArrayOnRemoved() {
        val set = IntHashSet(nullValue = -1)
        set.add(0)
        assertEquals(1, set.toArray().size)
        set.remove(0)
        assertEquals(0, set.toArray().size)
    }

    @Test
    fun clear() {
        val set = IntHashSet()
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
        val set = IntHashSet()
        assertEquals(0, set.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val set = IntHashSet()
        set.add(8)
        set.add(1)
        assertEquals(2, set.asSequence().count())
    }

    @Test
    fun randomOnEmpty() {
        assertFailsWith(NoSuchElementException::class) {
            val set = IntHashSet()
            set.random(Random)
        }
    }

    @Test
    fun randomOnSingleton() {
        val set = IntHashSet()
        set.add(12300)
        assertEquals(12300, set.random(Random))
    }

    @Test
    fun multipleRehash() {
        val set = IntHashSet(2)
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
        val set = IntHashSet()
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
    fun iteratorReentrant() {
        val s = IntHashSet()
        s.addAll(IntArray(10) { it + 1 })
        assertTrue(s.iterator().hasNext())
        assertEquals(10, s.iterator().asSequence().toSet().size)
        assertEquals(10, s.iterator().asSequence().toSet().size)
    }

    @Test
    fun copySame() {
        val s1 = IntHashSet()
        s1.addAll(generateSequence { Random.nextInt(0, Int.MAX_VALUE / 2) }.take(10).asIterable())
        val s2 = s1.copy()
        assertEquals(s1.size, s2.size)
        for (i in s1) {
            assertTrue(i in s2)
        }
    }

    @Test
    fun permutation() {
        val s1 = IntHashSet()
        s1.addAll(10..20)
        val s2 = s1.permutation(Random).asSequence().toSet()
        assertContentEquals(s1.toArray().also { it.sort() }, s2.toIntArray().also { it.sort() })
    }
}

class IntEntryTest {

    private fun test(key: Int, value: Int) {
        val entry = entry(key, value)
        assertEquals(key, entry.key(), "$key : $value")
        assertEquals(value, entry.value(), "$key : $value")
    }

    @Test
    fun keyValue() {
        for (i in -10..10) {
            test(i, i)
            test(i, -i)
        }
    }

    @Test
    fun keyValueBounds() {
        val values = intArrayOf(0, -1, Int.MAX_VALUE, Int.MIN_VALUE)
        for (v1 in values)
            for (v2 in values)
                test(v1, v2)
    }
}

class IntHashMapTest {

    @Test
    fun createEmpty() {
        val map = IntHashMap()
        assertEquals(0, map.size)
        assertTrue { map.isEmpty() }
    }

    @Test
    fun add() {
        val map = IntHashMap(nullKey = -1)
        entry(4, 4)
        for (i in 0 until 1000) {
            assertEquals(i, map.size)
            map.add(entry(i, i))
        }
    }

    @Test
    fun addDuplicate() {
        val map = IntHashMap(nullKey = -1)
        for (i in 0 until 1000) {
            assertEquals(i, map.size)
            assertEquals(0, map.add(entry(i, 1)))
            assertEquals(1, map.add(entry(i, 2)))
        }
    }

    @Test
    fun addNullKey() {
        assertFailsWith(IllegalArgumentException::class) {
            val map = IntHashMap()
            map[0] = 1
        }
        assertFailsWith(IllegalArgumentException::class) {
            val map = IntHashMap(nullKey = -1)
            map[-1] = 1
        }
    }

    @Test
    fun containsNotEmpty() {
        val map = IntHashMap()
        assertFalse(map.containsKey(map.nullKey))
        assertFalse(map.containsKey(10))
    }

    @Test
    fun containsAfterAdd() {
        val map = IntHashMap()
        map.add(entry(2, 10))
        assertTrue(map.containsKey(2))
    }

    @Test
    fun removeMissing() {
        val map = IntHashMap()
        assertEquals(0, map.remove(1))
        assertEquals(0, map.remove(0))
        assertEquals(0, map.remove(-1))
    }

    @Test
    fun removeAndAddAgain() {
        val map = IntHashMap()
        map[2] = 10
        map[8] = 1
        assertEquals(0, map.remove(3))
        assertEquals(2, map.size)
        assertEquals(10, map.remove(2))
        assertEquals(1, map.size)
        assertEquals(0, map.add(entry(2, 2)))
        assertEquals(2, map.size)
    }

    @Test
    fun clear() {
        val map = IntHashMap()
        for (i in 4..10)
            map[i] = i
        map.remove(5)
        map.clear()
        assertEquals(0, map.size)
        map.add(4)
        assertEquals(1, map.size)
    }

    @Test
    fun emptySequence() {
        val map = IntHashMap()
        assertEquals(0, map.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val map = IntHashMap()
        map.add(entry(8, 1))
        map.add(entry(1, 0))
        assertEquals(2, map.asSequence().count())
    }

    @Test
    fun multipleRehash() {
        val map = IntHashMap(2)
        (1..100).forEach { map.add(entry(it, it)) }
        map.clear()
        (1100..1120).forEach { map.add(entry(it, it)) }
        map.clear()
        (200..300).forEach { map.add(entry(it, it)) }
        assertEquals(101, map.size)
        assertFalse(map.containsKey(1))
        assertTrue(map.containsKey(200))
    }

    @Test
    fun largeRandomTest() {
        val r = Random(0)
        val all = ArrayList<Int>()
        val map = IntHashMap()
        val test = HashMap<Int, Int>()
        for (i in 1..1_000) {
            val n = r.nextInt(1, Int.MAX_VALUE)
            val v = r.nextInt(1, Int.MAX_VALUE)
            all.add(n)
            assertEquals(test.put(n, v) ?: 0, map.set(n, v))
            assertEquals(test.put(n, v) ?: 0, map.set(n, v))
            if (r.nextBoolean()) {
                val remove = all[r.nextInt(all.size)]
                assertEquals(test.remove(remove) ?: 0, map.remove(remove))
                assertEquals(test.remove(remove) ?: 0, map.remove(remove))
            }
        }
        for (i in all)
            assertEquals(test.remove(i) ?: 0, map.remove(i))
    }

    @Test
    fun iteratorReentrant() {
        val s = IntHashMap()
        for (i in 1..10) s.add(entry(i, Random.nextInt()))
        assertTrue(s.iterator().hasNext())
        assertEquals(10, s.iterator().asSequence().toSet().size)
        assertEquals(10, s.iterator().asSequence().toSet().size)
    }

    @Test
    fun copySame() {
        val s1 = IntHashMap(4, -1)
        for (i in 1..10) s1.add(entry(Random.nextInt(1, Int.MAX_VALUE), Random.nextInt()))
        val s2 = s1.copy()
        assertEquals(s1.size, s2.size)
        for (entry in s1) {
            assertTrue(s1.containsKey(entry.key()))
            assertEquals(s1[entry.key()], s2[entry.key()])
        }
    }
}
