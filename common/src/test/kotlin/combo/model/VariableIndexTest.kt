package combo.model

import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VariableIndexTest {

    @Test
    fun get() {
        val index1 = VariableIndex("a")
        val index2 = index1.addChildScope("b")

        val a = Flag("a", true)
        index1.add(a)
        val b = Flag("b", true)
        index2.add(b)

        assertEquals(a, index1["a"])
        assertEquals(b, index1["b"])

        assertNull(index2.find<Flag<*>>("a"))
        assertEquals(b, index2.find<Flag<*>>("b"))
    }

    @Test
    fun resolve() {
        val index1 = VariableIndex("a")
        val index2 = index1.addChildScope("b")

        val a = Flag("a", true)
        index1.add(a)
        val b = Flag("b", true)
        index2.add(b)

        assertEquals(a, index1.resolve("a"))
        assertFailsWith(NoSuchElementException::class) {
            index1.resolve("b")
        }

        assertEquals(a, index2.resolve("a"))
        assertEquals(b, index2.resolve("b"))
    }

    @Test
    fun resolveOverrides() {
        val index1 = VariableIndex("a")
        val index2 = index1.addChildScope("a")

        val a1 = Flag("a", true)
        index1.add(a1)
        val a2 = Flag("a", true)
        index2.add(a2)

        assertEquals(a1, index1.resolve("a"))
        assertEquals(a2, index2.resolve("a"))
    }

    @Test
    fun getChildScopes() {
        val index = VariableIndex("a")
        assertFailsWith(NoSuchElementException::class) {
            index.getChildScope("a")
        }
        val index2 = index.addChildScope("b")
        assertEquals(index2, index.getChildScope("b"))
        val index3 = index2.addChildScope("c")
        assertFailsWith(NoSuchElementException::class) {
            index.getChildScope("c")
        }
        assertEquals(index3, index2.getChildScope("c"))
    }

    @Test
    fun variableSequence() {
        val flags = Array(10) { Flag("$it", true) }
        val index = VariableIndex("")
        var prev = index
        val indices = Array(10) {
            prev = prev.addChildScope("$it")
            prev.add(flags[it])
            prev
        }

        val rootExpected = listOf(*((0 until 10).toList().toTypedArray()))
        assertEquals(rootExpected, index.asSequence().toList().map { it.name.toInt() })
        for (i in 0 until 10) {
            val expected = listOf(*((i until 10).toList().toTypedArray()))
            assertEquals(expected, indices[i].asSequence().toList().map { it.name.toInt() })
        }
    }

    @Test
    fun variableSequence2() {
        val flags = Array(10) { Flag("$it", true) }
        val index = VariableIndex("")
        index.add(flags[0])
        val fi1 = index.addChildScope("")
        fi1.add(flags[1])
        fi1.add(flags[2])
        val fi2 = fi1.addChildScope("")
        val fi3 = fi2.addChildScope("")
        fi3.add(flags[3])
        val fi4 = fi1.addChildScope("")
        fi4.add(flags[4])
        fi4.add(flags[5])
        fi4.add(flags[6])
        val fi5 = fi2.addChildScope("")
        fi5.add(flags[7])
        fi5.add(flags[8])
        fi5.add(flags[9])

        assertContentEquals(intArrayOf(0, 1, 2, 4, 5, 6, 7, 8, 9, 3), index.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(1, 2, 4, 5, 6, 7, 8, 9, 3), fi1.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(7, 8, 9, 3), fi2.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(3), fi3.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(4, 5, 6), fi4.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(7, 8, 9), fi5.asSequence().toList().map { it.name.toInt() }.toIntArray())
    }
}

