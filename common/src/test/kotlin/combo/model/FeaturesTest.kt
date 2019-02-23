package combo.model

import combo.sat.*
import combo.test.assertContentEquals
import combo.util.EMPTY_INT_ARRAY
import kotlin.test.*

class FlagTest {

    @Test
    fun nullIndexEntryValueOf() {
        val f = flag()
        val ie = f.createIndexEntry(intArrayOf(UNIT_FALSE))
        assertNull(ie.valueOf(BitFieldInstance(0)))
        assertNull(ie.valueOf(BitFieldInstance(1).apply { this[0] = true }))
        assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals(null))
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(true)
        }
    }

    @Test
    fun unitIndexEntry() {
        val f = flag()
        val ie = f.createIndexEntry(intArrayOf(UNIT_TRUE))
        assertEquals(true, ie.valueOf(BitFieldInstance(0)))
        assertEquals(true, ie.valueOf(BitFieldInstance(1)))
        assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals(true))
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(null)
        }
    }

    @Test
    fun normalIndexEntry() {
        val f = flag(1.0)
        val ie = f.createIndexEntry(intArrayOf(3))
        assertContentEquals(intArrayOf(6), ie.toLiterals(1.0))
        assertContentEquals(intArrayOf(7), ie.toLiterals(null))
        assertNull(ie.valueOf(BitFieldInstance(4)))
        assertEquals(1.0, ie.valueOf(BitFieldInstance(4).apply { this[3] = true }))
    }

    @Test
    fun missingValue() {
        val f = flag(2, "a")
        val ie = f.createIndexEntry(intArrayOf(1))
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(4)
        }
    }
}


class SelectTest {
    @Test
    fun referencesTest() {
        val a = alternative(2.0, 4.5, 9.0, 12.0, name = "a")
        assertEquals(5, a.variables.size)
    }
}

class OptionTest {

    @Test
    fun normalOptionTest() {
        val a = alternative(1.0, 2.0, 3.0)
        val o = a.option(1.0).not()
        assertEquals(a, o.rootFeature)
        assertEquals(7, o.toLiteral(2))
    }

    @Test
    fun missingOptionTest() {
        val o = multiple(1.0, 2.0, 3.0)
        assertFailsWith(IllegalArgumentException::class) {
            o.option(1.2)
        }
    }

    @Test
    fun referencesTest() {
        val a = alternative(1.0, 2.0, 2.5)
        assertEquals(1, a.option(2.0).variables.size)
    }
}

class AlternativeTest {

    @Test
    fun nullRootIndexEntry() {
        val f = alternative(1, 2, 3)
        val ie = f.createIndexEntry(IntArray(4) { UNIT_FALSE })
        assertNull(ie.valueOf(BitFieldInstance(0)))
        assertNull(ie.valueOf(BitFieldInstance(1).apply { this[0] = true }))
        for (i in 1 until 3)
            assertFailsWith(UnsatisfiableException::class) {
                ie.toLiterals(i)
            }
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(4)
        }
        assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals(null))
    }

    @Test
    fun unitRootIndexEntry() {
        val f = alternative(4, 5, 6)
        val ie = f.createIndexEntry(intArrayOf(UNIT_TRUE, 2, 3, 4))
        for (i in 4..6) {
            assertContentEquals(intArrayOf((i - 2).toLiteral(true)), ie.toLiterals(i))
            assertEquals(i, ie.valueOf(BitFieldInstance(5).apply { this[i - 2] = true }))
        }
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(7)
        }
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(null)
        }
    }

    @Test
    fun singleOption() {
        val f = alternative(1)
        val ie = f.createIndexEntry(intArrayOf(2, 3))
        assertEquals(1, ie.valueOf(BitFieldInstance(4).apply { this[2] = true;this[3] = true }))
        assertNull(ie.valueOf(BitFieldInstance(2)))
        assertContentEquals(intArrayOf(6), ie.toLiterals(1))
        assertContentEquals(intArrayOf(5), ie.toLiterals(null))
    }

    @Test
    fun normalIndexEntry() {
        val f = alternative(1, 2, 3)
        val ie = f.createIndexEntry(intArrayOf(0, 1, 2, 3))
        assertNull(ie.valueOf(BitFieldInstance(4)))
        assertEquals(3, ie.valueOf(BitFieldInstance(4).apply { this[0] = true; this[3] = true }))
        for (i in 1..3)
            assertContentEquals(intArrayOf((i).toLiteral(true)), ie.toLiterals(i))
        assertContentEquals(intArrayOf(1), ie.toLiterals(null))
        assertFailsWith(IllegalArgumentException::class) {
            assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals(4))
        }
    }

    @Test
    fun missingValue() {
        val f = alternative(listOf(1, 3, 2), "a")
        val ie = f.createIndexEntry(intArrayOf(0, 1, 2, 4))
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(4)
        }
    }

    @Test
    fun allOptionsNullIndexEntry() {
        val f = alternative("a", "d", "b")
        val ie = f.createIndexEntry(intArrayOf(0, UNIT_FALSE, UNIT_FALSE, UNIT_FALSE))
        assertNull(ie.valueOf(BitFieldInstance(4)))
        assertFailsWith(ValidationException::class) {
            ie.toLiterals("a")
        }
    }

    @Test
    fun oneOptionNullIndexEntry() {
        val f = alternative("a", "b", "c", name = "n")
        val ie = f.createIndexEntry(intArrayOf(0, UNIT_FALSE, 1, 2))
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals("a")
        }
        assertContentEquals(intArrayOf(1), ie.toLiterals(null))
        assertContentEquals(intArrayOf(2), ie.toLiterals("b"))
        assertContentEquals(intArrayOf(4), ie.toLiterals("c"))
        assertNull(ie.valueOf(BitFieldInstance(3)))
        assertEquals("b", ie.valueOf(BitFieldInstance(3).apply { this[0] = true; this[1] = true }))
        assertEquals("c", ie.valueOf(BitFieldInstance(3).apply { this[0] = true; this[2] = true }))
    }

    @Test
    fun unitOptionIndexEntry() {
        val f = alternative("a", "b", "c")
        val ie = f.createIndexEntry(intArrayOf(UNIT_TRUE, UNIT_TRUE, UNIT_FALSE, UNIT_FALSE))
        for (id in ie.indices) assertTrue(id < 0)
        assertEquals("a", ie.valueOf(BitFieldInstance(0)))
        assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals("a"))
        assertFailsWith(ValidationException::class) {
            assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals("b"))
        }
    }
}

class MultipleTest {

    @Test
    fun nullRootIndexEntry() {
        val f = multiple(1, 2, 3)
        val ie = f.createIndexEntry(IntArray(4) { UNIT_FALSE })
        assertNull(ie.valueOf(ByteArrayInstance(0)))
        assertNull(ie.valueOf(ByteArrayInstance(1).apply { this[0] = true }))
        for (i in 1 until 3)
            assertFailsWith(ValidationException::class) {
                ie.toLiterals(i)
            }
    }

    @Test
    fun unitRootIndexEntry() {
        val f = multiple(1, 2, 3)
        val ie = f.createIndexEntry(intArrayOf(UNIT_TRUE, 0, 1, 2))
        assertEquals(setOf(1), ie.valueOf(BitFieldInstance(3).apply { this[0] = true }))
        assertEquals(setOf(2), ie.valueOf(BitFieldInstance(3).apply { this[1] = true }))
        assertEquals(setOf(3), ie.valueOf(BitFieldInstance(3).apply { this[2] = true }))
        assertEquals(setOf(1, 2, 3), ie.valueOf(BitFieldInstance(3).apply { setAll(intArrayOf(0, 2, 4)) }))
        assertEquals(setOf(1, 2), ie.valueOf(BitFieldInstance(3).apply { setAll(intArrayOf(0, 2)) }))
        assertEquals(setOf(2, 3), ie.valueOf(BitFieldInstance(3).apply { setAll(intArrayOf(2, 4)) }))
        for (i in 1 until 3)
            assertContentEquals(intArrayOf((i - 1).toLiteral(true)), ie.toLiterals(setOf(i)))
        assertContentEquals(intArrayOf(0, 4), ie.toLiterals(setOf(1, 3)))
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(setOf(1, 11, 3, 10))
        }
        assertFailsWith(IllegalStateException::class) {
            ie.valueOf(BitFieldInstance(3))
        }
    }

    @Test
    fun singleOption() {
        val f = multiple(1)
        val ie = f.createIndexEntry(intArrayOf(2, 3))
        assertEquals(setOf(1), ie.valueOf(BitFieldInstance(4).apply { this[2] = true;this[3] = true }))
        assertNull(ie.valueOf(BitFieldInstance(2)))
        assertContentEquals(intArrayOf(6), ie.toLiterals(setOf(1)))
        assertContentEquals(intArrayOf(5), ie.toLiterals(null))
    }

    @Test
    fun normalIndexEntry() {
        val f = multiple(1, 2, 3)
        val ie = f.createIndexEntry(intArrayOf(2, 3, 4, 5))
        assertNull(ie.valueOf(BitFieldInstance(6)))
        assertEquals(setOf(1, 2, 3), ie.valueOf(IntSetInstance(6).apply { setAll(intArrayOf(4, 6, 8, 10)) }))
        assertEquals(setOf(3), ie.valueOf(IntSetInstance(6).apply { this[2] = true; this[5] = true }))
        assertEquals(setOf(3), ie.valueOf(IntSetInstance(6).apply { this[2] = true; this[5] = true }))
        assertContentEquals(intArrayOf(5), ie.toLiterals(null))
        assertContentEquals(intArrayOf(6, 8, 10), ie.toLiterals(setOf(1, 2, 3)))
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(setOf(1, 2, 4))
        }
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(emptySet<Int>())
        }
    }

    @Test
    fun notACollection() {
        val f = multiple(listOf(10, 5, 3))
        val ie = f.createIndexEntry(intArrayOf(0, 1, 2, 3))
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(4)
        }
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(3)
        }
    }

    @Test
    fun missingValue() {
        val f = multiple(listOf(1, 2, 3), "a")
        val ie = f.createIndexEntry(intArrayOf(0, 1, 2, 3))
        assertContentEquals(intArrayOf(1), ie.toLiterals(null))
        assertFailsWith(IllegalArgumentException::class) {
            ie.toLiterals(setOf(4))
        }
    }

    @Test
    fun allOptionsNullIndexEntry() {
        val f = multiple("a", "d", "b")
        val ie = f.createIndexEntry(IntArray(4) { UNIT_FALSE })
        assertNull(ie.valueOf(BitFieldInstance(4)))
        assertFailsWith(ValidationException::class) {
            ie.toLiterals(setOf("a"))
        }
    }

    @Test
    fun oneOptionNullIndexEntry() {
        val f = multiple("a", "b", "c", name = "n")
        val ie = f.createIndexEntry(intArrayOf(0, UNIT_FALSE, 1, 2))
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(setOf("a"))
        }
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(setOf("c", "a"))
        }
        assertContentEquals(intArrayOf(1), ie.toLiterals(null))
        assertContentEquals(intArrayOf(2), ie.toLiterals(setOf("b")))
        assertContentEquals(intArrayOf(2, 4), ie.toLiterals(setOf("c", "b")))
        assertNull(ie.valueOf(BitFieldInstance(3)))
        assertEquals(setOf("b"), ie.valueOf(BitFieldInstance(3).apply { this[0] = true; this[1] = true }))
        assertEquals(setOf("c"), ie.valueOf(BitFieldInstance(3).apply { this[0] = true; this[2] = true }))
    }

    @Test
    fun unitOptionIndexEntry() {
        val f = multiple("a", "b", "c")
        val ie = f.createIndexEntry(intArrayOf(UNIT_TRUE, UNIT_TRUE, 1, 2))
        assertFailsWith(UnsatisfiableException::class) {
            ie.toLiterals(null)
        }
        assertContentEquals(EMPTY_INT_ARRAY, ie.toLiterals(setOf("a")))
        assertContentEquals(intArrayOf(4), ie.toLiterals(setOf("c", "a")))
        assertEquals(setOf("a", "b", "c"), ie.valueOf(BitFieldInstance(3, LongArray(1) { 0b111 })))
        assertEquals(setOf("a"), ie.valueOf(BitFieldInstance(3, LongArray(1) { 0b001 })))
        assertEquals(setOf("a", "c"), ie.valueOf(BitFieldInstance(3, LongArray(1) { 0b101 })))
    }
}
