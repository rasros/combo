package combo.sat

import combo.math.IntPermutation
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class ConstraintTest {
    fun randomExhaustivePropagations(cs: Array<Constraint>) {
        // This test thoroughly test that unit propagation does not change the truth value of an instance.
        // It iteratively calls unitPropagation on each literal in the instance.
        val rng = Random.Default
        for (c in cs) for (i in c.literals) require(i.toIx() <= 4)
        for (l in InstancePermutation(5, BitArrayFactory, rng)) {
            for (c in cs) {
                //try {
                val c2 = IntPermutation(l.size, rng).iterator().asSequence().fold(c) { s: Constraint, i ->
                    val v = l[i]
                    val unit = i.toLiteral(v)
                    val cp = s.unitPropagation(unit)
                    val sSat = s.satisfies(l)
                    val cSat = c.satisfies(l)
                    val cpSat = cp.satisfies(l)
                    if (cSat != cpSat) {
                        s.unitPropagation(i.toLiteral(v))
                        println()
                    }
                    assertEquals(c.satisfies(l), cp.satisfies(l))
                    cp
                }
                assertTrue(c2.isUnit() || c2 == Tautology || c2 == Empty, "$c + ${l.toLiterals().joinToString()} -> $c2")
                //} catch (e: UnsatisfiableException) {
                //if (c.satisfies(l))
                //throw e
                //}
            }
        }
    }

    fun randomFlipViolations(instance: MutableInstance, constraint: Constraint) {
        val pre = constraint.cache(instance)
        assertEquals(constraint.violations(instance), constraint.violations(instance, pre), "$pre: $instance")
        val lit = constraint.literals.random(Random)
        val updatedMatches = constraint.cacheUpdate(instance, pre, !instance.literal(lit.toIx()))
        instance.flip(lit.toIx())
        val post = constraint.cache(instance)
        assertEquals(post, updatedMatches, instance.toString())
        assertEquals(constraint.violations(instance), constraint.violations(instance, updatedMatches))
    }

}

/*
class CardinalityTest {

    @Test
    fun nonPositiveFail() {
        assertFailsWith(IllegalArgumentException::class) {
            Cardinality(IntList(intArrayOf(1)), 1, Relation.EQ)
        }
    }

    @Test
    fun degreeUnsatisfiableExactlyFail() {
        assertFailsWith(ValidationException::class) {
            Cardinality(IntList(intArrayOf(0, 2)), 3, Relation.EQ)
        }
    }

    @Test
    fun degreeUnsatisfiableAtLeastFail() {
        assertFailsWith(ValidationException::class) {
            Cardinality(IntList(intArrayOf(0, 2)), 3, Relation.GE)
        }
    }

    @Test
    fun degreeUnsatisfiableAtMost() {
        Cardinality(IntList(intArrayOf(0, 2)), 3, Relation.LE)
    }

    @Test
    fun emptyLiteralsFails() {
        assertFailsWith(IllegalArgumentException::class) {
            Cardinality(IntList(intArrayOf()), 1, Relation.LE)
        }
    }

    @Test
    fun satisfiesBlank() {
        val instance = IntSetInstance(4)
        assertTrue(Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE).satisfies(instance))
        assertEquals(0, Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE).flipsToSatisfy(instance))
    }

    @Test
    fun satisfies() {
        val e = Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE)
        assertTrue(e.satisfies(BitArray(3, LongArray(1) { 0b000 })))
        assertTrue(e.satisfies(BitArray(3, LongArray(1) { 0b001 })))
        assertTrue(e.satisfies(BitArray(3, LongArray(1) { 0b010 })))
        assertFalse(e.satisfies(BitArray(3, LongArray(1) { 0b011 })))
        assertTrue(e.satisfies(BitArray(3, LongArray(1) { 0b100 })))
        assertFalse(e.satisfies(BitArray(3, LongArray(1) { 0b101 })))
        assertFalse(e.satisfies(BitArray(3, LongArray(1) { 0b110 })))
        assertFalse(e.satisfies(BitArray(3, LongArray(1) { 0b1110 })))
    }

    @Test
    fun flipsToSatisfyMatches() {
        val c1 = Cardinality(IntList(intArrayOf(0, 2, 4)), 2, Relation.LE)
        assertEquals(0, c1.flipsToSatisfy(0))
        assertEquals(0, c1.flipsToSatisfy(1))
        assertEquals(0, c1.flipsToSatisfy(2))
        assertEquals(1, c1.flipsToSatisfy(3))

        val c2 = Cardinality(IntList(intArrayOf(0, 2, 4)), 2, Relation.GE)
        assertEquals(2, c2.flipsToSatisfy(0))
        assertEquals(1, c2.flipsToSatisfy(1))
        assertEquals(0, c2.flipsToSatisfy(2))
        assertEquals(0, c2.flipsToSatisfy(3))

        val c3 = Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.EQ)
        assertEquals(1, c3.flipsToSatisfy(0))
        assertEquals(0, c3.flipsToSatisfy(1))
        assertEquals(1, c3.flipsToSatisfy(2))
        assertEquals(2, c3.flipsToSatisfy(3))
    }

    @Test
    fun updateMatches() {
        val c = Disjunction(IntList(intArrayOf(2, 7)))
        for (k in 0 until 16) {
            val instance = BitArray(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(instance, c)
        }
    }

    @Test
    fun unitPropagationNone() {
        val a = Cardinality(IntList(intArrayOf(2, 8, 10)), 1, Relation.LE)
        assertContentEquals(a.literals.toArray().apply { sort() }, a.propagateUnit(4).literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, a.propagateUnit(6).literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, a.propagateUnit(12).literals.toArray().apply { sort() })
    }

    @Test
    fun unitPropagationNoneDegree() {
        val a = Cardinality(IntList(intArrayOf(2, 6, 8)), 2, Relation.GE)
        assertContentEquals(a.literals.toArray().apply { sort() }, a.propagateUnit(0).literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, a.propagateUnit(4).literals.toArray().apply { sort() })
    }

    @Test
    fun unitPropagationAtMost() {
        val a = Cardinality(IntList(intArrayOf(2, 4)), 1, Relation.LE)
        val b = a.propagateUnit(2)
        assertFalse(b.satisfies(BitArray(3, longArrayOf(0b111))))
        assertTrue(b.satisfies(BitArray(3, longArrayOf(0b000))))
    }

    @Test
    fun unitPropagationFailAtLeast() {
        val a = Cardinality(IntList(intArrayOf(2, 4)), 1, Relation.GE)
        assertFailsWith(ValidationException::class) {
            a.propagateUnit(3).propagateUnit(5)
        }
    }

    @Test
    fun unitPropagationFailDegree() {
        val a = Cardinality(IntList(intArrayOf(0, 2, 4, 6)), 3, Relation.GE)
        assertFailsWith(ValidationException::class) {
            a.propagateUnit(1).propagateUnit(3)
        }
    }

    @Test
    fun unitPropagationDegreeDecrease() {
        val a = Cardinality(IntList(intArrayOf(0, 2, 4, 6)), 3, Relation.GE)
        val b = a.propagateUnit(0)
        assertEquals(2, (b as Cardinality).degree)
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 1, Relation.LE),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 3, Relation.LE),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 1, Relation.LT),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 3, Relation.LT),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 1, Relation.GE),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 3, Relation.GE),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 1, Relation.GT),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 3, Relation.GT),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 1, Relation.EQ),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 3, Relation.EQ),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 1, Relation.NE),
                Cardinality(IntList(intArrayOf(0, 2, 4, 6, 8)), 3, Relation.NE)))
    }
}

class ReifiedTest {

    @Test
    fun emptyFails() {
        assertFailsWith(IllegalArgumentException::class) {
            ReifiedEquivalent(0, Disjunction(IntList(intArrayOf())))
        }
    }

    @Test
    fun tautologyFails() {
        assertFailsWith(IllegalArgumentException::class) {
            ReifiedEquivalent(0, Tautology)
        }
    }

    @Test
    fun satisfiesDisjunction() {
        val d = ReifiedEquivalent(1, Disjunction(IntList(intArrayOf(2, 5))))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b111 })))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b011 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b101 })))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b001 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b110 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b010 })))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b100 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun flipsToSatisfyDisjunction() {
        val d = ReifiedEquivalent(1, Disjunction(IntList(intArrayOf(2, 5))))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b111 })))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b011 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b101 })))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b001 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b110 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b010 })))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b100 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun satisfiesConjunction() {
        val d = ReifiedEquivalent(0, Conjunction(IntList(intArrayOf(2, 5))))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b111 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b011 })))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b101 })))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b001 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b110 })))
        assertFalse(d.satisfies(BitArray(3, LongArray(1) { 0b010 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b100 })))
        assertTrue(d.satisfies(BitArray(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun flipsToSatisfyConjunction() {
        val d = ReifiedEquivalent(0, Conjunction(IntList(intArrayOf(2, 5))))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b111 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b011 })))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b101 })))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b001 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b110 })))
        assertEquals(1, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b010 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b100 })))
        assertEquals(0, d.flipsToSatisfy(BitArray(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun updateMatchesConjunction() {
        val c = Conjunction(IntList(intArrayOf(2, 7)))
        val r = ReifiedEquivalent(0, c)
        for (k in 0 until 16) {
            val instance = BitArray(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(instance, r)
        }
    }

    @Test
    fun updateMatchesDisjunction() {
        val d = Disjunction(IntList(intArrayOf(3, 6)))
        val r = ReifiedEquivalent(1, d)
        for (k in 0 until 16) {
            val instance = BitArray(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(instance, r)
        }
    }

    @Test
    fun toCnfConjunction() {
        val e = ReifiedEquivalent(1, Conjunction(IntList(intArrayOf(2, 4))))
        val c = e.toCnf().toList<Constraint>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toSet()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toSet()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfDisjunction() {
        val e = ReifiedEquivalent(2, Disjunction(IntList(intArrayOf(0, 5))))
        val c = e.toCnf().toList<Constraint>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toList()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toList()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfSatisfiesDisjunction() {
        val original = ReifiedEquivalent(5, Disjunction(IntList(intArrayOf(1, 3))))
        val toCnf = original.toCnf()
        for (l in InstancePermutation(3, BitArrayFactory, Random)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toCnfSatisfiesConjunction() {
        val original = ReifiedEquivalent(4, Conjunction(IntList(intArrayOf(0, 3))))
        val toCnf = original.toCnf()
        for (l in InstancePermutation(3, BitArrayFactory, Random)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun propagateUnitReturnsReifiedClause() {
        val r = ReifiedEquivalent(4, Disjunction(IntList(intArrayOf(1, 3, 7))))
        val clause = r.propagateUnit(4)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(1, 3, 7), clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagateNegUnitNegatesClauseDisjunction() {
        val r = ReifiedEquivalent(4, Disjunction(IntList(intArrayOf(1, 3, 7))))
        val clause = r.propagateUnit(5)
        assertTrue(clause is Conjunction)
        assertContentEquals(intArrayOf(0, 2, 6), clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagateNegUnitNegatesClauseConjunction() {
        val r = ReifiedEquivalent(4, Conjunction(IntList(intArrayOf(1, 3, 7))))
        val clause = r.propagateUnit(5)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(0, 2, 6), clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagatePosUnitConjunction() {
        val r = ReifiedEquivalent(2, Conjunction(IntList(intArrayOf(0, 4, 6, 8))))
        val s = r.propagateUnit(8)
        assertContentEquals(intArrayOf(0, 4, 6), (s as ReifiedEquivalent).clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagatePosUnitDisjunction() {
        val r = ReifiedEquivalent(2, Disjunction(IntList(intArrayOf(0, 4, 6, 8))))
        val s = r.propagateUnit(8)
        assertContentEquals(intArrayOf(2), (s as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastNegConjunction() {
        val r = ReifiedEquivalent(0, Conjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(5).propagateUnit(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastPosConjunction() {
        val r = ReifiedEquivalent(0, Conjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(2).propagateUnit(4)
        assertContentEquals(intArrayOf(0), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastNegDisjunction() {
        val r = ReifiedEquivalent(0, Disjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(5).propagateUnit(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastPosDisjunction() {
        val r = ReifiedEquivalent(0, Disjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(2).propagateUnit(4)
        assertContentEquals(intArrayOf(0), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                ReifiedEquivalent(0, Conjunction(IntList(intArrayOf(2, 4, 6, 8)))),
                ReifiedEquivalent(2, Conjunction(IntList(intArrayOf(0, 4, 6, 8)))),
                ReifiedEquivalent(0, Disjunction(IntList(intArrayOf(2, 4, 6, 8)))),
                ReifiedEquivalent(2, Disjunction(IntList(intArrayOf(0, 4, 6, 8))))))
    }
}

class RelationTest {
    @Test
    fun eq() {
        assertEquals(1, Relation.EQ.flipsToSatisfy(3, 2))
        assertEquals(1, Relation.EQ.flipsToSatisfy(1, 2))
        assertEquals(0, Relation.EQ.flipsToSatisfy(2, 2))
        assertEquals(0, Relation.EQ.flipsToSatisfy(0, 0))
    }

    @Test
    fun ne() {
        assertEquals(0, Relation.NE.flipsToSatisfy(3, 2))
        assertEquals(0, Relation.NE.flipsToSatisfy(1, 2))
        assertEquals(0, Relation.NE.flipsToSatisfy(0, 2))
        assertEquals(1, Relation.NE.flipsToSatisfy(0, 0))
    }

    @Test
    fun le() {
        assertEquals(1, Relation.LE.flipsToSatisfy(3, 2))
        assertEquals(0, Relation.LE.flipsToSatisfy(1, 2))
        assertEquals(0, Relation.LE.flipsToSatisfy(2, 2))
        assertEquals(0, Relation.LE.flipsToSatisfy(0, 0))
    }

    @Test
    fun lt() {
        assertEquals(2, Relation.LT.flipsToSatisfy(3, 2))
        assertEquals(0, Relation.LT.flipsToSatisfy(1, 2))
        assertEquals(1, Relation.LT.flipsToSatisfy(2, 2))
        assertEquals(1, Relation.LT.flipsToSatisfy(0, 0))
    }

    @Test
    fun ge() {
        assertEquals(0, Relation.GE.flipsToSatisfy(3, 2))
        assertEquals(1, Relation.GE.flipsToSatisfy(1, 2))
        assertEquals(2, Relation.GE.flipsToSatisfy(0, 2))
        assertEquals(0, Relation.GE.flipsToSatisfy(0, 0))
    }

    @Test
    fun gt() {
        assertEquals(0, Relation.GT.flipsToSatisfy(3, 2))
        assertEquals(2, Relation.GT.flipsToSatisfy(1, 2))
        assertEquals(3, Relation.GT.flipsToSatisfy(0, 2))
        assertEquals(1, Relation.GT.flipsToSatisfy(0, 0))
    }
}
*/
