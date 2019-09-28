package combo.model

import combo.test.assertContentEquals
import kotlin.test.*


class ScopeTest {

    @Test
    fun find() {
        val scope1 = RootScope(Root("a"))
        val scope2 = scope1.addScope("b", Flag("b", true))

        val a = Flag("a", true)
        scope1.add(a)
        val b = Flag("b", true)
        scope2.add(b)

        assertEquals(a, scope1.find("a")!!)
        assertEquals(b, scope1.find("b")!!)

        assertNull(scope2.find<Flag<*>>("a"))
        assertEquals(b, scope2.find<Flag<*>>("b"))
    }

    @Test
    fun resolve() {
        val scope1 = RootScope(Root("a"))
        val scope2 = scope1.addScope("b", Flag("b", true))

        val a = Flag("a", true)
        scope1.add(a)
        val b = Flag("b", true)
        scope2.add(b)

        assertTrue(scope1.inScope("a"))
        assertEquals(a, scope1.resolve("a"))
        assertFalse(scope1.inScope("b"))
        assertFailsWith(NoSuchElementException::class) {
            scope1.resolve("b")
        }

        assertEquals(a, scope2.resolve("a"))
        assertEquals(b, scope2.resolve("b"))
    }

    @Test
    fun resolveMissing() {
        val scope1 = RootScope(Root("a"))
        val scope2 = scope1.addScope("b", Flag("b", true))

        val a = Flag("a", true)
        scope1.add(a)
        val b = Flag("b", true)
        scope2.add(b)

        assertFalse(scope1.inScope("c"))
        assertFalse(scope2.inScope("c"))
        assertFailsWith(NoSuchElementException::class) {
            scope1.resolve("c")
        }
        assertFailsWith(NoSuchElementException::class) {
            scope2.resolve("c")
        }
    }

    @Test
    fun resolveOverrides() {
        val scope1 = RootScope(Root("a"))
        val scope2 = scope1.addScope("a", Flag("a", true))

        val a1 = Flag("a", true)
        scope1.add(a1)
        val a2 = Flag("a", true)
        scope2.add(a2)

        assertEquals(a1, scope1.resolve("a"))
        assertEquals(a2, scope2.resolve("a"))
    }

    @Test
    fun variableSequence() {
        val flags = Array(10) { Flag("$it", true) }
        val scope = RootScope(Root(""))
        var prev: Scope = scope
        val indices = Array(10) {
            prev = prev.addScope("$it", Flag("$it", true))
            prev.add(flags[it])
            prev
        }

        val rootExpected = listOf(*((0 until 10).toList().toTypedArray()))
        assertEquals(rootExpected, scope.asSequence().toList().map { it.name.toInt() })
        for (i in 0 until 10) {
            val expected = listOf(*((i until 10).toList().toTypedArray()))
            assertEquals(expected, indices[i].asSequence().toList().map { it.name.toInt() })
        }
    }

    @Test
    fun variableSequence2() {
        val flags = Array(10) { Flag("$it", true) }
        val scope = RootScope(Root(""))
        scope.add(flags[0])
        val fi1 = scope.addScope("", Flag("1", true))
        fi1.add(flags[1])
        fi1.add(flags[2])
        val fi2 = fi1.addScope("", Flag("2", true))
        val fi3 = fi2.addScope("", Flag("3", true))
        fi3.add(flags[3])
        val fi4 = fi1.addScope("", Flag("4", true))
        fi4.add(flags[4])
        fi4.add(flags[5])
        fi4.add(flags[6])
        val fi5 = fi2.addScope("", Flag("5", true))
        fi5.add(flags[7])
        fi5.add(flags[8])
        fi5.add(flags[9])

        assertContentEquals(intArrayOf(0, 1, 2, 4, 5, 6, 7, 8, 9, 3), scope.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(1, 2, 4, 5, 6, 7, 8, 9, 3), fi1.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(7, 8, 9, 3), fi2.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(3), fi3.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(4, 5, 6), fi4.asSequence().toList().map { it.name.toInt() }.toIntArray())
        assertContentEquals(intArrayOf(7, 8, 9), fi5.asSequence().toList().map { it.name.toInt() }.toIntArray())
    }
}
