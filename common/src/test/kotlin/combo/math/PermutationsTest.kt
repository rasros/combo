package combo.math

import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntPermutationTest {
    @Test
    fun emptyPermutation() {
        assertFalse(IntPermutation(0, Random).iterator().hasNext())
    }

    @Test
    fun sameTwice() {
        val p = IntPermutation(100, Random)
        for (i in 0 until 100) {
            assertEquals(p.encode(i), p.encode(i))
        }
    }

    @Test
    fun exhaustiveEvenPermutation() {
        val p = IntPermutation(2.0.pow(4).toInt(), Random)
        val set = HashSet<Int>()
        for (i in 0 until 2.0.pow(4).toInt()) {
            set.add(p.encode(i))
        }
        assertEquals(2.0.pow(4).toInt(), set.size)
        for (i in 0 until 2.0.pow(4).toInt()) {
            assertTrue(set.contains(i))
        }
    }

    @Test
    fun exhaustiveOddPermutation() {
        val p = IntPermutation(1001, Random)
        val set = HashSet<Int>()
        for (i in 0 until 1001) {
            set.add(p.encode(i))
        }
        assertEquals(1001, set.size)
    }

    @Test
    fun iterator() {
        val l = IntPermutation(4, Random).iterator().asSequence().toList()
        assertEquals(4, l.size)
        assertEquals(4, l.toSet().size)
    }
}

class LongPermutationTest {

    @Test
    fun emptyPermutation() {
        assertFalse(LongPermutation(0, Random).iterator().hasNext())
    }

    @Test
    fun sameTwice() {
        val p = LongPermutation(100, Random)
        for (i in 0 until 100L) {
            assertEquals(p.encode(i), p.encode(i))
        }
    }

    @Test
    fun exhaustiveEvenPermutation() {
        val p = LongPermutation(2.0.pow(4).toLong(), Random)
        val set = HashSet<Long>()
        for (i in 0 until 2.0.pow(4).toLong()) {
            set.add(p.encode(i))
        }
        assertEquals(2.0.pow(4).toInt(), set.size)
        for (i in 0 until 2.0.pow(4).toLong()) {
            assertTrue(set.contains(i))
        }
    }

    @Test
    fun exhaustiveOddPermutation() {
        val p = LongPermutation(1001, Random)
        val set = HashSet<Long>()
        for (i in 0 until 1001L) {
            set.add(p.encode(i))
        }
        assertEquals(1001, set.size)
    }

    @Test
    fun iterator() {
        val l = LongPermutation(4, Random).iterator().asSequence().toList()
        assertEquals(4, l.size)
        assertEquals(4, l.toSet().size)
    }
}
