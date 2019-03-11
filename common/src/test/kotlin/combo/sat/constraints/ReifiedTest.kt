package combo.sat.constraints

import combo.sat.*
import combo.sat.solvers.ExhaustiveSolver
import combo.test.assertContentEquals
import combo.util.IntList
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ReifiedEquivalentTest : ConstraintTest() {

    @Test
    fun violationsDisjunction() {
        val d = ReifiedEquivalent(-1, Disjunction(IntList(intArrayOf(2, -3))))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b111 })))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b011 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b101 })))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b001 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b110 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b010 })))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b100 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b000 })))
    }

    @Test
    fun violationsConjunction() {
        val d = ReifiedEquivalent(1, Conjunction(IntList(intArrayOf(2, -3))))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b111 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b011 })))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b101 })))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b001 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b110 })))
        assertEquals(1, d.violations(BitArray(3, IntArray(1) { 0b010 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b100 })))
        assertEquals(0, d.violations(BitArray(3, IntArray(1) { 0b000 })))
    }

    @Test
    fun updateCache() {
        fun testUpdateCache(c: NegatableConstraint) {
            val r = ReifiedEquivalent(-1, c)
            for (k in 0 until 16) {
                val instance = BitArray(4, IntArray(1) { k })
                randomCacheUpdates(instance, r)
            }
        }
        for (c in arrayOf(
                Conjunction(collectionOf(2, -4)),
                Disjunction(collectionOf(2, 3)),
                Disjunction(collectionOf(-2, -3)),
                Cardinality(collectionOf(2, 3, 4), 2, Relation.EQ),
                Cardinality(collectionOf(2, 3, 4), 1, Relation.LE),
                Cardinality(collectionOf(2, 3, 4), 1, Relation.NE),
                ReifiedEquivalent(-2, Disjunction(collectionOf(3, -4)))))
            testUpdateCache(c)
    }

    @Test
    fun toCnfConjunction() {
        val e = ReifiedEquivalent(-1, Conjunction(IntList(intArrayOf(2, 3))))
        val c = e.toCnf().toList<Constraint>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toSet()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toSet()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfDisjunction() {
        val e = ReifiedEquivalent(2, Disjunction(IntList(intArrayOf(1, -3))))
        val c = e.toCnf().toList<Constraint>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toList()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toList()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfSatisfiesDisjunction() {
        val original = ReifiedEquivalent(-3, Disjunction(IntList(intArrayOf(-1, -2))))
        val toCnf = original.toCnf()
        for (l in InstancePermutation(3, BitArrayFactory, Random)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toCnfSatisfiesConjunction() {
        val original = ReifiedEquivalent(3, Conjunction(IntList(intArrayOf(1, -2))))
        val toCnf = original.toCnf()
        for (l in InstancePermutation(3, BitArrayFactory, Random)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun propagateUnitReturnsReifiedConstraint() {
        val r = ReifiedEquivalent(3, Disjunction(IntList(intArrayOf(-1, -2, -4))))
        val clause = r.unitPropagation(3)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(-1, -2, -4), clause.literals.sortedBy { it.toIx() }.toIntArray())
    }

    @Test
    fun propagateNegUnitNegatesClauseDisjunction() {
        val r = ReifiedEquivalent(3, Disjunction(IntList(intArrayOf(-1, -2, -4))))
        val clause = r.unitPropagation(-3)
        assertTrue(clause is Conjunction)
        assertContentEquals(intArrayOf(1, 2, 4), clause.literals.sortedBy { it.toIx() }.toIntArray())
    }

    @Test
    fun propagateNegUnitNegatesClauseConjunction() {
        val r = ReifiedEquivalent(4, Conjunction(IntList(intArrayOf(-1, -2, -3))))
        val clause = r.unitPropagation(-4)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(1, 2, 3), clause.literals.sortedBy { it.toIx() }.toIntArray())
    }

    @Test
    fun propagatePosUnitConjunction() {
        val r = ReifiedEquivalent(2, Conjunction(IntList(intArrayOf(1, 3, 4, 5))))
        val s = r.unitPropagation(5)
        assertContentEquals(intArrayOf(1, 3, 4), (s as ReifiedEquivalent).constraint.literals.sortedBy { it.toIx() }.toIntArray())
    }

    @Test
    fun propagatePosUnitDisjunction() {
        val r = ReifiedEquivalent(2, Disjunction(IntList(intArrayOf(1, 3, 4, 5))))
        val s = r.unitPropagation(5)
        assertContentEquals(intArrayOf(2), (s as Conjunction).literals.toArray())
    }

    @Test
    fun propagateLastNegConjunction() {
        val r = ReifiedEquivalent(1, Conjunction(IntList(intArrayOf(2, 3))))
        val c = r.unitPropagation(5).unitPropagation(-2)
        assertContentEquals(intArrayOf(-1), (c as Conjunction).literals.toArray())
    }

    @Test
    fun propagateLastPosConjunction() {
        val r = ReifiedEquivalent(1, Conjunction(IntList(intArrayOf(2, 3))))
        val c = r.unitPropagation(2).unitPropagation(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals.toArray())
    }

    @Test
    fun propagateLastNegDisjunction() {
        val r = ReifiedEquivalent(1, Disjunction(IntList(intArrayOf(2, 3))))
        val c = r.unitPropagation(-3).unitPropagation(-2)
        assertContentEquals(intArrayOf(-1), (c as Conjunction).literals.toArray())
    }

    @Test
    fun propagateLastPosDisjunction() {
        val r = ReifiedEquivalent(1, Disjunction(IntList(intArrayOf(2, 3))))
        val c = r.unitPropagation(2).unitPropagation(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals.toArray())
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                ReifiedEquivalent(1, Conjunction(collectionOf(2, 3, 4, 5))),
                ReifiedEquivalent(5, Conjunction(collectionOf(-1, -2, 3, -4))),
                ReifiedEquivalent(2, Conjunction(collectionOf(-3, -4))),
                ReifiedEquivalent(1, Disjunction(collectionOf(2, 3, 4, 5))),
                ReifiedEquivalent(2, Disjunction(collectionOf(1, 3, 4, 5))),
                ReifiedEquivalent(1, Cardinality(collectionOf(2, 3, 4, 5), 2, Relation.EQ)),
                ReifiedEquivalent(2, Cardinality(collectionOf(1, 3, 4, 5), 1, Relation.GE))))
    }
}
