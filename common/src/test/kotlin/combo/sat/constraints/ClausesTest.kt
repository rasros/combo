package combo.sat.constraints

import combo.sat.BitArray
import combo.sat.ConstraintTest
import combo.sat.Empty
import combo.sat.Tautology
import combo.test.assertContentEquals
import combo.util.IntList
import combo.util.collectionOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConjunctionTest : ConstraintTest() {

    @Test
    fun satisfies() {
        val instance = BitArray(4, IntArray(1) { 0b0110 })
        assertFalse(Conjunction(IntList(intArrayOf(1))).satisfies(instance))
        assertTrue(Conjunction(IntList(intArrayOf(2))).satisfies(instance))
        assertFalse(Conjunction(IntList(intArrayOf(1, 3))).satisfies(instance))
        assertFalse(Conjunction(IntList(intArrayOf(1, -3))).satisfies(instance))
        assertFalse(Conjunction(IntList(intArrayOf(1, -4))).satisfies(instance))
        assertTrue(Conjunction(IntList(intArrayOf(2, -4))).satisfies(instance))
    }

    @Test
    fun violations() {
        val instance = BitArray(4, IntArray(1) { 0b0110 })
        assertEquals(1, Conjunction(IntList(intArrayOf(1))).violations(instance))
        assertEquals(0, Conjunction(IntList(intArrayOf(2))).violations(instance))
        assertEquals(1, Conjunction(IntList(intArrayOf(1, 3))).violations(instance))
        assertEquals(2, Conjunction(IntList(intArrayOf(1, -3))).violations(instance))
        assertEquals(1, Conjunction(IntList(intArrayOf(1, -4))).violations(instance))
        assertEquals(0, Conjunction(IntList(intArrayOf(2, -4))).violations(instance))
    }

    @Test
    fun violationsEmpty() {
        val c = Conjunction(IntList(intArrayOf()))
        assertEquals(0, c.violations(BitArray(0), 0))
    }

    @Test
    fun updateCache() {
        val c = Conjunction(IntList(intArrayOf(1, -4)))
        for (k in 0 until 16) {
            val instance = BitArray(4, IntArray(1) { k })
            randomCacheUpdates(instance, c)
        }
    }

    @Test
    fun unitPropagationNone() {
        val a = Conjunction(IntList(intArrayOf(2, -4)))
        val b = a.unitPropagation(-5)
        val c = a.unitPropagation(3)
        assertContentEquals(a.literals.toArray().apply { sort() }, b.literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, c.literals.toArray().apply { sort() })
    }

    @Test
    fun unitPropagationComplete() {
        val a = Conjunction(IntList(intArrayOf(2, -4)))
        assertEquals(Tautology, a.unitPropagation(2).unitPropagation(-4))
    }

    @Test
    fun unitPropagationFail() {
        val a = Conjunction(IntList(intArrayOf(1, 2)))
        assertEquals(Empty, a.unitPropagation(-2))
        assertEquals(Empty, a.unitPropagation(-1))
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(Conjunction(collectionOf(-2)))
        randomExhaustivePropagations(Conjunction(collectionOf(1, -2, 5)))
        randomExhaustivePropagations(Conjunction(collectionOf(1, 2, 3, 4)))
        randomExhaustivePropagations(Conjunction(collectionOf(2, 3, 4, 5)))
        randomExhaustivePropagations(Conjunction(collectionOf(-1, -2, -3, -4)))
        randomExhaustivePropagations(Conjunction(collectionOf(2, 3, -4, -5)))
    }

    @Test
    fun randomCoerce() {
        randomCoerce(Conjunction(collectionOf(1, 4, 5)))
        randomCoerce(Conjunction(collectionOf(1, -4, 5)))
        randomCoerce(Conjunction(collectionOf(1, 5)))
        randomCoerce(Conjunction(collectionOf(1, -5)))
        randomCoerce(Conjunction(collectionOf(-3)))
    }
}

class DisjunctionTest : ConstraintTest() {

    @Test
    fun satisfies() {
        val instance = BitArray(4, IntArray(1) { 0b0110 })
        assertFalse(Disjunction(IntList(intArrayOf(-2, -3))).satisfies(instance))
        assertTrue(Disjunction(IntList(intArrayOf(-2, -1))).satisfies(instance))
        assertTrue(Disjunction(IntList(intArrayOf(1, 3))).satisfies(instance))
        assertFalse(Disjunction(IntList(intArrayOf(1, -3))).satisfies(instance))
        assertTrue(Disjunction(IntList(intArrayOf(1, -4))).satisfies(instance))
    }

    @Test
    fun violations() {
        val instance = BitArray(4, IntArray(1) { 0b0110 })
        assertEquals(1, Disjunction(IntList(intArrayOf(-2, -3))).violations(instance))
        assertEquals(0, Disjunction(IntList(intArrayOf(-2, -1))).violations(instance))
        assertEquals(0, Disjunction(IntList(intArrayOf(1, 3))).violations(instance))
        assertEquals(1, Disjunction(IntList(intArrayOf(1, -3))).violations(instance))
        assertEquals(0, Disjunction(IntList(intArrayOf(1, -4))).violations(instance))
    }

    @Test
    fun violationsEmpty() {
        val c = Disjunction(IntList(intArrayOf()))
        assertEquals(0, c.violations(BitArray(0), 0))
    }

    @Test
    fun updateCache() {
        val c = Disjunction(IntList(intArrayOf(1, -4)))
        for (k in 0 until 16) {
            val instance = BitArray(4, IntArray(1) { k })
            randomCacheUpdates(instance, c)
        }
    }

    @Test
    fun unitPropagation() {
        val a = Disjunction(IntList(intArrayOf(1, 2, 3)))
        val a1 = a.unitPropagation(2)
        assertEquals(a1, Tautology)
        val a2 = a.unitPropagation(-2)
        assertEquals(2, a2.literals.size)
        assertTrue(1 in a2.literals)
        assertFalse(2 in a2.literals)
        assertTrue(3 in a2.literals)

        val b = Disjunction(IntList(intArrayOf(-1, 2, 3)))
        val d1 = b.unitPropagation(-1)
        assertEquals(d1, Tautology)
        val d2 = b.unitPropagation(1)
        assertEquals(2, d2.literals.size)
        assertFalse(-1 in d2.literals)
        assertTrue(2 in d2.literals)
        assertTrue(3 in d2.literals)
    }

    @Test
    fun unitPropagationNone() {
        val a = Disjunction(IntList(intArrayOf(-1, -4)))
        val b = a.unitPropagation(-5)
        assertContentEquals(a.literals.toArray().apply { sort() }, b.literals.toArray().apply { sort() })
    }

    @Test
    fun unitPropagationFail() {
        val a = Disjunction(IntList(intArrayOf(2, -3)))
        assertEquals(Empty, a.unitPropagation(-2).unitPropagation(3))
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(Disjunction(collectionOf(-2)))
        randomExhaustivePropagations(Disjunction(collectionOf(1, -2, 5)))
        randomExhaustivePropagations(Disjunction(collectionOf(1, 2, 3, 4)))
        randomExhaustivePropagations(Disjunction(collectionOf(2, 3, 4, 5)))
        randomExhaustivePropagations(Disjunction(collectionOf(-1, -2, -3, -4)))
        randomExhaustivePropagations(Disjunction(collectionOf(-1, 3, -4, -5)))
    }

    @Test
    fun randomCoerce() {
        randomCoerce(Disjunction(collectionOf(1, 4, 5)))
        randomCoerce(Disjunction(collectionOf(1, -4, 5)))
        randomCoerce(Disjunction(collectionOf(1, 5)))
        randomCoerce(Disjunction(collectionOf(1, -5)))
        randomCoerce(Disjunction(collectionOf(-3)))
    }
}
