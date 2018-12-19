package combo.util

import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeTest {
    class TestTree(override val value: String, override val children: List<TestTree>) : Tree<String, TestTree>

    private val a = TestTree("a", emptyList())
    private val b = TestTree("b", emptyList())
    private val c = TestTree("c", emptyList())
    private val d = TestTree("d", emptyList())
    private val e = TestTree("e", listOf(a, b))
    private val f = TestTree("f", listOf(c, d, e))
    private val g = TestTree("g", emptyList())
    private val h = TestTree("h", listOf(g, f))

    @Test
    fun iterationOrder() {
        val list = (h.asSequence().map { it.value }.toList())
        assertContentEquals(listOf("h", "g", "f", "c", "d", "e", "a", "b"), list)
    }

    @Test
    fun size() {
        assertEquals(1, a.size)
        assertEquals(3, e.size)
        assertEquals(6, f.size)
        assertEquals(8, h.size)
    }

    @Test
    fun leaves() {
        val leaves = h.leaves().map { it.value }.toList()
        assertContentEquals(listOf("g", "c", "d", "a", "b"), leaves)
    }

    @Test
    fun containsAll() {
        assertTrue(f.contains(a))
        assertTrue(f.contains(b))
        assertTrue(f.containsAll(listOf(a, b)))
    }

    @Test
    fun depth() {
        assertEquals(1, a.depth())
        assertEquals(1, c.depth())
        assertEquals(2, e.depth())
        assertEquals(3, f.depth())
        assertEquals(4, h.depth())
    }
}
