package combo.model

import combo.sat.BitFieldInstance
import combo.sat.IntSetInstance
import combo.test.assertContentEquals
import kotlin.test.*

class AssignmentTest {
    @Test
    fun getBooleanFlag() {
        val f = flag()
        val m = Model.builder().optional(f).build()
        assertEquals(true, m.toAssignment(BitFieldInstance(1, LongArray(1) { 0b1 }))[f])
        assertEquals(false, m.toAssignment(BitFieldInstance(1, LongArray(1) { 0b0 }))[f])
    }

    @Test
    fun getFlag() {
        val f = flag(10)
        val m = Model.builder().optional(f).build()
        assertEquals(10, m.toAssignment(BitFieldInstance(1, LongArray(1) { 0b1 }))[f])
        assertEquals(null, m.toAssignment(BitFieldInstance(1, LongArray(1) { 0b0 }))[f])
    }

    @Test
    fun getOr() {
        val f = multiple(10, 20)
        val m = Model.builder().mandatory(f).build()
        assertEquals(setOf(10), m.toAssignment(BitFieldInstance(2, LongArray(1) { 0b01 }))[f])
        assertEquals(setOf(10, 20), m.toAssignment(BitFieldInstance(2, LongArray(1) { 0b11 }))[f])
    }

    @Test
    fun getAlternative() {
        val f = alternative("a", "b")
        val m = Model.builder().optional(f).build()
        assertNull(m.toAssignment(BitFieldInstance(3, LongArray(1) { 0b000 }))[f])
        assertEquals("a", m.toAssignment(BitFieldInstance(3, LongArray(3) { 0b011 }))[f])
        assertEquals("b", m.toAssignment(BitFieldInstance(3, LongArray(3) { 0b101 }))[f])
    }

    @Test
    fun getOrThrow() {
        val f = flag(value = "a", name = "f")
        val m = Model.builder().optional(f).build()
        assertFailsWith(NoSuchElementException::class) {
            assertNull(m.toAssignment(BitFieldInstance(3, LongArray(1) { 0b000 })).getOrThrow(f))
        }
        assertEquals("a", m.toAssignment(BitFieldInstance(3, LongArray(3) { 0b011 })).getOrThrow(f))
    }

    @Test
    fun getOrDefault() {
        val f = flag(value = "a", name = "f")
        val m = Model.builder().optional(f).build()
        assertEquals("b", m.toAssignment(BitFieldInstance(3, LongArray(1) { 0b000 })).getOrDefault(f, "b"))
        assertEquals("a", m.toAssignment(BitFieldInstance(3, LongArray(3) { 0b011 })).getOrDefault(f, "b"))
    }

    @Test
    fun iterator() {
        val root = flag()
        val m = Model.builder(root)
                .optional(flag())
                .optional(alternative(1..5))
                .optional(multiple(1..5)).build()
        val a = m.toAssignment(BitFieldInstance(13))
        for (amt in a) {
            if (amt.feature != root)
                assertNull(amt.value)
        }
    }

    @Test
    fun map() {
        val f1 = flag()
        val a1 = alternative(1..5)
        val or1 = multiple(1..5)
        val m = Model.builder().optional(f1).optional(a1).optional(or1).build()
        val a = m.toAssignment(IntSetInstance(m.problem.nbrVariables).apply { this.setAll(intArrayOf(0, 2, 12)) })
        val map = a.map
        assertEquals(true, map[f1])
        assertEquals(5, map[a1])
        assertEquals(4, map.size)
        assertEquals(setOf(m.features[0], f1, a1, or1), map.keys)
        assertContentEquals(listOf(true, true, 5, null), map.values.toList())
    }

    @Test
    fun contains() {
        val f1 = flag()
        val f2 = flag()
        val m = Model.builder().optional(f1).build()
        val a = m.toAssignment(BitFieldInstance(1))
        assertTrue(a.containsKey(f1))
        assertFalse(a.containsKey(f2))
        assertTrue(a.containsValue(true))
    }
}
