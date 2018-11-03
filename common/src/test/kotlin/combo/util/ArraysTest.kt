package combo.util

import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArraysTest {
    @Test
    fun applyTransformToEmptyIntArray() {
        assertTrue { intArrayOf().applyTransform { it + 1 }.isEmpty() }
    }

    @Test
    fun applyTransformToIntArray() {
        assertTrue { intArrayOf(1, 2, 3).contentEquals(intArrayOf(0, 1, 2).applyTransform { it + 1 }) }
    }

    @Test
    fun applyTransformToEmptyDoubleArray() {
        assertTrue { doubleArrayOf().applyTransform { it / 0.0 }.isEmpty() }
    }

    @Test
    fun applyTransformToDoubleArray() {
        assertTrue { doubleArrayOf(1.0, 2.0, 3.0).contentEquals(doubleArrayOf(2.0, 4.0, 6.0).applyTransform { it / 2.0 }) }
    }

    @Test
    fun shouldNotCopyArray() {
        val array = IntArray(10)
        assertSame(array, array.applyTransform { it + 1 })
    }

    @Test
    fun removeOne() {
        assertTrue(intArrayOf(1).remove(0).isEmpty())
    }

    @Test
    fun removeLast() {
        assertContentEquals(intArrayOf(1, 2, 3), intArrayOf(1, 2, 3, 4).remove(3))
    }

    @Test
    fun removeMiddle() {
        assertContentEquals(intArrayOf(1, 3, 4), intArrayOf(1, 2, 3, 4).remove(1))
        assertContentEquals(intArrayOf(1, 2, 4), intArrayOf(1, 2, 3, 4).remove(2))
    }

    @Test
    fun removeFirst() {
        assertContentEquals(intArrayOf(2, 3, 4), intArrayOf(1, 2, 3, 4).remove(0))
    }
}
