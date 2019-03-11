package combo.sat.constraints

import combo.sat.BitArray
import combo.sat.ConstraintTest
import combo.sat.SparseBitArray
import combo.sat.constraints.Relation.*
import combo.test.assertContentEquals
import combo.util.IntList
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardinalityTest : ConstraintTest() {

    @Test
    fun satisfiesBlank() {
        val instance = SparseBitArray(4)
        assertTrue(Cardinality(IntList(intArrayOf(1, 2, 3)), 1, LE).satisfies(instance))
        assertEquals(0, Cardinality(collectionOf(1, 2, 3), 1, LE).violations(instance))
    }

    @Test
    fun satisfies() {
        val e = Cardinality(collectionOf(1, 2, 3), 1, LE)
        assertTrue(e.satisfies(BitArray(3, IntArray(1) { 0b000 })))
        assertTrue(e.satisfies(BitArray(3, IntArray(1) { 0b001 })))
        assertTrue(e.satisfies(BitArray(3, IntArray(1) { 0b010 })))
        assertFalse(e.satisfies(BitArray(3, IntArray(1) { 0b011 })))
        assertTrue(e.satisfies(BitArray(3, IntArray(1) { 0b100 })))
        assertFalse(e.satisfies(BitArray(3, IntArray(1) { 0b101 })))
        assertFalse(e.satisfies(BitArray(3, IntArray(1) { 0b110 })))
        assertFalse(e.satisfies(BitArray(3, IntArray(1) { 0b1110 })))
    }

    @Test
    fun violations() {
        val instances = arrayOf(
                BitArray(3, IntArray(1) { 0b000 }),
                BitArray(3, IntArray(1) { 0b001 }),
                BitArray(3, IntArray(1) { 0b101 }),
                BitArray(3, IntArray(1) { 0b111 }))

        fun testConstraint(degree: Int, relation: Relation, expectedMatches: IntArray) {
            val c = Cardinality(collectionOf(1, 2, 3), degree, relation)
            for (i in 0..3)
                assertEquals(expectedMatches[i], c.violations(instances[i]), "$i")
        }

        testConstraint(0, LE, intArrayOf(0, 1, 2, 3))
        testConstraint(1, LE, intArrayOf(0, 0, 1, 2))
        testConstraint(2, LE, intArrayOf(0, 0, 0, 1))
        testConstraint(3, LE, intArrayOf(0, 0, 0, 0))

        testConstraint(1, LT, intArrayOf(0, 1, 2, 3))
        testConstraint(2, LT, intArrayOf(0, 0, 1, 2))
        testConstraint(3, LT, intArrayOf(0, 0, 0, 1))
        testConstraint(4, LT, intArrayOf(0, 0, 0, 0))

        testConstraint(0, EQ, intArrayOf(0, 1, 2, 3))
        testConstraint(1, EQ, intArrayOf(1, 0, 1, 2))
        testConstraint(2, EQ, intArrayOf(2, 1, 0, 1))
        testConstraint(3, EQ, intArrayOf(3, 2, 1, 0))

        testConstraint(0, NE, intArrayOf(1, 0, 0, 0))
        testConstraint(1, NE, intArrayOf(0, 1, 0, 0))
        testConstraint(2, NE, intArrayOf(0, 0, 1, 0))
        testConstraint(3, NE, intArrayOf(0, 0, 0, 1))

        testConstraint(0, GE, intArrayOf(0, 0, 0, 0))
        testConstraint(1, GE, intArrayOf(1, 0, 0, 0))
        testConstraint(2, GE, intArrayOf(2, 1, 0, 0))
        testConstraint(3, GE, intArrayOf(3, 2, 1, 0))

        testConstraint(0, GT, intArrayOf(1, 0, 0, 0))
        testConstraint(1, GT, intArrayOf(2, 1, 0, 0))
        testConstraint(2, GT, intArrayOf(3, 2, 1, 0))
    }

    @Test
    fun updateCache() {
        val c = Cardinality(IntList(intArrayOf(1, 2, 3)), 1, EQ)
        for (k in 0 until 16) {
            val instance = BitArray(4, IntArray(1) { k })
            randomCacheUpdates(instance, c)
        }
    }

    @Test
    fun unitPropagationNone() {
        val a = Cardinality(IntList(intArrayOf(1, 5, 6)), 1, Relation.LE)
        assertContentEquals(a.literals.toArray().apply { sort() }, a.unitPropagation(2).literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, a.unitPropagation(4).literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, a.unitPropagation(8).literals.toArray().apply { sort() })
    }

    @Test
    fun randomExhaustivePropagations() {
        val lits = collectionOf(1, 2, 3, 4, 5)
        randomExhaustivePropagations(arrayOf(
                Cardinality(lits, 1, Relation.LE),
                Cardinality(lits, 3, Relation.LE),
                Cardinality(lits, 1, Relation.LT),
                Cardinality(lits, 3, Relation.LT),
                Cardinality(lits, 1, Relation.GE),
                Cardinality(lits, 3, Relation.GE),
                Cardinality(lits, 1, Relation.GT),
                Cardinality(lits, 3, Relation.GT),
                Cardinality(lits, 1, Relation.EQ),
                Cardinality(lits, 3, Relation.EQ),
                Cardinality(lits, 1, Relation.NE),
                Cardinality(lits, 3, Relation.NE)))
    }
}

class RelationTest {
    @Test
    fun eq() {
        assertEquals(1, Relation.EQ.violations(3, 2))
        assertEquals(1, Relation.EQ.violations(1, 2))
        assertEquals(0, Relation.EQ.violations(2, 2))
        assertEquals(0, Relation.EQ.violations(0, 0))
    }

    @Test
    fun ne() {
        assertEquals(0, Relation.NE.violations(3, 2))
        assertEquals(0, Relation.NE.violations(1, 2))
        assertEquals(0, Relation.NE.violations(0, 2))
        assertEquals(1, Relation.NE.violations(0, 0))
    }

    @Test
    fun le() {
        assertEquals(1, Relation.LE.violations(3, 2))
        assertEquals(0, Relation.LE.violations(1, 2))
        assertEquals(0, Relation.LE.violations(2, 2))
        assertEquals(0, Relation.LE.violations(0, 0))
    }

    @Test
    fun lt() {
        assertEquals(2, Relation.LT.violations(3, 2))
        assertEquals(0, Relation.LT.violations(1, 2))
        assertEquals(1, Relation.LT.violations(2, 2))
        assertEquals(1, Relation.LT.violations(0, 0))
    }

    @Test
    fun ge() {
        assertEquals(0, Relation.GE.violations(3, 2))
        assertEquals(1, Relation.GE.violations(1, 2))
        assertEquals(2, Relation.GE.violations(0, 2))
        assertEquals(0, Relation.GE.violations(0, 0))
    }

    @Test
    fun gt() {
        assertEquals(0, Relation.GT.violations(3, 2))
        assertEquals(2, Relation.GT.violations(1, 2))
        assertEquals(3, Relation.GT.violations(0, 2))
        assertEquals(1, Relation.GT.violations(0, 0))
    }
}
