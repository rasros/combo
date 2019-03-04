package combo.util

import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.*

class IntRangeSetTest {

    @Test
    fun createEmpty() {
        val set = IntRangeSet(0, -1)
        assertEquals(0, set.size)
        assertFalse(set.contains(-1))
        assertFalse(set.contains(0))
        assertFalse(set.contains(1))
        assertTrue(set.isEmpty())
    }

    @Test
    fun createPointSet() {
        val set = IntRangeSet(0, 0)
        assertEquals(1, set.size)
        assertFalse(set.isEmpty())
    }

    @Test
    fun containsPointSet() {
        val set = IntRangeSet(0, 0)
        assertFalse(set.contains(-1))
        assertTrue(set.contains(0))
        assertFalse(set.contains(1))
    }

    @Test
    fun toArrayOnEmpty() {
        val set = IntRangeSet(0, -1)
        assertTrue(set.toArray().isEmpty())
    }

    @Test
    fun toArray() {
        val set = IntRangeSet(10, 19)
        assertContentEquals((10..19).toList().toIntArray(), set.toArray())
    }

    @Test
    fun emptySequence() {
        val set = IntRangeSet(-2, -3)
        assertEquals(0, set.asSequence().count())
    }

    @Test
    fun smallSequence() {
        val set = IntRangeSet(2, 3)
        assertEquals(2, set.asSequence().count())
    }

    @Test
    fun randomOnEmpty() {
        assertFailsWith(NoSuchElementException::class) {
            val set = IntRangeSet(5, 4)
            set.random(Random)
        }
    }

    @Test
    fun randomOnSingleton() {
        val set = IntRangeSet(5, 5)
        assertEquals(5, set.random(Random))
    }

    @Test
    fun iteratorReentrant() {
        val s = IntRangeSet(1, 10)
        assertTrue(s.iterator().hasNext())
        assertEquals(10, s.iterator().asSequence().toSet().size)
        assertEquals(10, s.iterator().asSequence().toSet().size)
    }

    @Test
    fun copySame() {
        val s1 = IntRangeSet(100, 200)
        val s2 = s1.copy()
        assertEquals(s1.size, s2.size)
        for (i in s1) {
            assertTrue(i in s2)
        }
    }

    @Test
    fun permutation() {
        val s1 = IntRangeSet(10, 20)
        val s2 = s1.permutation(Random).asSequence().toSet()
        assertContentEquals(s1.toArray().also { it.sort() }, s2.toIntArray().also { it.sort() })
    }

    @Test
    fun emptyPermutation() {
        assertEquals(0, IntRangeSet(0, -1).permutation(Random).asSequence().count())
    }
}