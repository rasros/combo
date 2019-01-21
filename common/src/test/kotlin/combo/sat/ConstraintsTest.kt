package combo.sat

import combo.math.IntPermutation
import combo.sat.solvers.ExhaustiveSolver
import combo.test.assertContentEquals
import combo.util.IntList
import kotlin.random.Random
import kotlin.test.*

private fun randomExhaustivePropagations(cs: Array<Constraint>) {
    // This test thoroughly test that unit propagation does not change the truth value of a labeling.
    val rng = Random.Default
    for (c in cs) for (i in c.literals) require(i.toIx() <= 4)
    for (l in LabelingPermutation.sequence(5, rng)) {
        for (c in cs) {
            try {
                val c2 = IntPermutation(l.size, rng).iterator().asSequence().fold(c) { s: Constraint, i ->
                    val v = l[i]
                    val cp = s.propagateUnit(i.toLiteral(v))
                    assertEquals(c.satisfies(l), cp.satisfies(l))
                    cp
                }
                assertTrue(c2.isUnit() || c2 == Tautology, "$c + ${l.toLiterals(false).joinToString()} -> $c2")
            } catch (e: UnsatisfiableException) {
                if (c.satisfies(l))
                    throw e
            }
        }
    }
}

private fun checkUpdateMatches(labeling: MutableLabeling, constraint: Constraint) {
    val pre = constraint.matches(labeling)
    assertEquals(constraint.flipsToSatisfy(labeling), constraint.flipsToSatisfy(pre), "" + pre + ": " + labeling.toString())
    val lit = constraint.literals.random(Random)
    labeling.flip(lit.toIx())
    val updatedMatches = constraint.matchesUpdate(labeling.literal(lit.toIx()), pre)
    val post = constraint.matches(labeling)
    assertEquals(post, updatedMatches, labeling.toString())
}

class ConjunctionTest {

    @Test
    fun satisfies() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertFalse(Conjunction(IntList(intArrayOf(0))).satisfies(l))
        assertTrue(Conjunction(IntList(intArrayOf(1))).satisfies(l))
        assertFalse(Conjunction(IntList(intArrayOf(0, 4))).satisfies(l))
        assertFalse(Conjunction(IntList(intArrayOf(0, 5))).satisfies(l))
        assertFalse(Conjunction(IntList(intArrayOf(0, 7))).satisfies(l))
        assertTrue(Conjunction(IntList(intArrayOf(1, 7))).satisfies(l))
    }

    @Test
    fun flipsToSatisfy() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertEquals(1, Conjunction(IntList(intArrayOf(0))).flipsToSatisfy(l))
        assertEquals(0, Conjunction(IntList(intArrayOf(1))).flipsToSatisfy(l))
        assertEquals(1, Conjunction(IntList(intArrayOf(0, 4))).flipsToSatisfy(l))
        assertEquals(2, Conjunction(IntList(intArrayOf(0, 5))).flipsToSatisfy(l))
        assertEquals(1, Conjunction(IntList(intArrayOf(0, 7))).flipsToSatisfy(l))
        assertEquals(0, Conjunction(IntList(intArrayOf(1, 7))).flipsToSatisfy(l))
    }

    @Test
    fun flipsToSatisfyEmpty() {
        val c = Conjunction(IntList(intArrayOf()))
        assertEquals(0, c.flipsToSatisfy(0))
    }

    @Test
    fun flipsToSatisfyMatches() {
        val c = Conjunction(IntList(intArrayOf(0, 2)))
        assertEquals(2, c.flipsToSatisfy(0))
        assertEquals(1, c.flipsToSatisfy(1))
        assertEquals(0, c.flipsToSatisfy(2))
    }

    @Test
    fun updateMatches() {
        val c = Conjunction(IntList(intArrayOf(2, 7)))
        for (k in 0 until 16) {
            val labeling = BitFieldLabeling(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(labeling, c)
        }
    }

    @Test
    fun unitPropagationNone() {
        val a = Conjunction(IntList(intArrayOf(2, 7)))
        val b = a.propagateUnit(9)
        val c = a.propagateUnit(7)
        assertContentEquals(a.literals.toArray().apply { sort() }, b.literals.toArray().apply { sort() })
        assertContentEquals(a.literals.toArray().apply { sort() }, c.literals.toArray().apply { sort() })
    }

    @Test
    fun unitPropagationFail1() {
        assertFailsWith(UnsatisfiableException::class) {
            val a = Conjunction(IntList(intArrayOf(2, 4)))
            a.propagateUnit(3)
            a.propagateUnit(5)
            fail()
        }
    }

    @Test
    fun unitPropagationFail2() {
        assertFailsWith(ValidationException::class) {
            val a = Conjunction(IntList(intArrayOf(2, 4)))
            a.propagateUnit(5)
        }
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Conjunction(IntList(intArrayOf(3))),
                Conjunction(IntList(intArrayOf(0, 3, 8))),
                Conjunction(IntList(intArrayOf(0, 2, 4, 6))),
                Conjunction(IntList(intArrayOf(2, 4, 6, 8))),
                Conjunction(IntList(intArrayOf(2, 4, 7, 9)))))
    }
}

class DisjunctionTest {

    @Test
    fun satisfies() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertFalse(Disjunction(IntList(intArrayOf(3, 5))).satisfies(l))
        assertTrue(Disjunction(IntList(intArrayOf(3, 1))).satisfies(l))
        assertTrue(Disjunction(IntList(intArrayOf(0, 4))).satisfies(l))
        assertFalse(Disjunction(IntList(intArrayOf(0, 5))).satisfies(l))
        assertTrue(Disjunction(IntList(intArrayOf(0, 7))).satisfies(l))
    }

    @Test
    fun flipsToSatisfy() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertEquals(1, Disjunction(IntList(intArrayOf(3, 5))).flipsToSatisfy(l))
        assertEquals(0, Disjunction(IntList(intArrayOf(3, 1))).flipsToSatisfy(l))
        assertEquals(0, Disjunction(IntList(intArrayOf(0, 4))).flipsToSatisfy(l))
        assertEquals(1, Disjunction(IntList(intArrayOf(0, 5))).flipsToSatisfy(l))
        assertEquals(0, Disjunction(IntList(intArrayOf(0, 7))).flipsToSatisfy(l))
    }

    @Test
    fun flipsToSatisfyEmpty() {
        val c = Disjunction(IntList(intArrayOf()))
        assertEquals(0, c.flipsToSatisfy(0))
    }

    @Test
    fun flipsToSatisfyMatches() {
        val c = Disjunction(IntList(intArrayOf(0, 5)))
        assertEquals(1, c.flipsToSatisfy(0))
        assertEquals(0, c.flipsToSatisfy(1))
        assertEquals(0, c.flipsToSatisfy(2))
    }

    @Test
    fun unitPropagation() {
        val a = Disjunction(IntList(intArrayOf(0, 2, 4)))
        val a1 = a.propagateUnit(2)
        assertEquals(a1, Tautology)
        val a2 = a.propagateUnit(3)
        assertEquals(2, a2.literals.size)
        assertTrue(0 in a2.literals)
        assertTrue(4 in a2.literals)

        val b = Disjunction(IntList(intArrayOf(1, 2, 4)))
        val d1 = b.propagateUnit(1)
        assertEquals(d1, Tautology)
        val d2 = b.propagateUnit(0)
        assertEquals(2, d2.literals.size)
        assertTrue(2 in d2.literals)
        assertTrue(4 in d2.literals)
    }

    @Test
    fun unitPropagationNone() {
        val a = Disjunction(IntList(intArrayOf(2, 7)))
        val b = a.propagateUnit(9)
        assertContentEquals(a.literals.toArray().apply { sort() }, b.literals.toArray().apply { sort() })
    }

    @Test
    fun unitPropagationFail() {
        assertFailsWith(UnsatisfiableException::class) {
            val a = Disjunction(IntList(intArrayOf(2, 5)))
            a.propagateUnit(3).propagateUnit(4)
        }
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Disjunction(IntList(intArrayOf(3))),
                Disjunction(IntList(intArrayOf(0, 3, 8))),
                Disjunction(IntList(intArrayOf(0, 2, 4, 6))),
                Disjunction(IntList(intArrayOf(2, 4, 6, 8))),
                Disjunction(IntList(intArrayOf(1, 4, 7, 9)))))
    }
}

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
        val l = IntSetLabeling(4)
        assertTrue(Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE).satisfies(l))
        assertEquals(0, Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE).flipsToSatisfy(l))
    }

    @Test
    fun satisfies() {
        val e = Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE)
        assertTrue(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b000 })))
        assertTrue(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertTrue(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertFalse(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertTrue(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertFalse(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertFalse(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertFalse(e.satisfies(BitFieldLabeling(3, LongArray(1) { 0b1110 })))
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
            val labeling = BitFieldLabeling(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(labeling, c)
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
        assertFalse(b.satisfies(BitFieldLabeling(3, longArrayOf(0b111))))
        assertTrue(b.satisfies(BitFieldLabeling(3, longArrayOf(0b000))))
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
            Reified(0, Disjunction(IntList(intArrayOf())))
        }
    }

    @Test
    fun tautologyFails() {
        assertFailsWith(IllegalArgumentException::class) {
            Reified(0, Tautology)
        }
    }

    @Test
    fun satisfiesDisjunction() {
        val d = Reified(1, Disjunction(IntList(intArrayOf(2, 5))))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b111 })))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun flipsToSatisfyDisjunction() {
        val d = Reified(1, Disjunction(IntList(intArrayOf(2, 5))))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b111 })))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun satisfiesConjunction() {
        val d = Reified(0, Conjunction(IntList(intArrayOf(2, 5))))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b111 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertFalse(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertTrue(d.satisfies(BitFieldLabeling(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun flipsToSatisfyConjunction() {
        val d = Reified(0, Conjunction(IntList(intArrayOf(2, 5))))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b111 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun updateMatchesConjunction() {
        val c = Conjunction(IntList(intArrayOf(2, 7)))
        val r = Reified(0, c)
        for (k in 0 until 16) {
            val labeling = BitFieldLabeling(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(labeling, r)
        }
    }

    @Test
    fun updateMatchesDisjunction() {
        val d = Disjunction(IntList(intArrayOf(3, 6)))
        val r = Reified(1, d)
        for (k in 0 until 16) {
            val labeling = BitFieldLabeling(4, LongArray(1) { k.toLong() })
            checkUpdateMatches(labeling, r)
        }
    }

    @Test
    fun toCnfConjunction() {
        val e = Reified(1, Conjunction(IntList(intArrayOf(2, 4))))
        val c = e.toCnf().toList<Constraint>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toSet()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toSet()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfDisjunction() {
        val e = Reified(2, Disjunction(IntList(intArrayOf(0, 5))))
        val c = e.toCnf().toList<Constraint>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toList()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toList()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfSatisfiesDisjunction() {
        val original = Reified(5, Disjunction(IntList(intArrayOf(1, 3))))
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3, Random.Default)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toCnfSatisfiesConjunction() {
        val original = Reified(4, Conjunction(IntList(intArrayOf(0, 3))))
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3, Random.Default)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun propagateUnitReturnsReifiedClause() {
        val r = Reified(4, Disjunction(IntList(intArrayOf(1, 3, 7))))
        val clause = r.propagateUnit(4)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(1, 3, 7), clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagateNegUnitNegatesClauseDisjunction() {
        val r = Reified(4, Disjunction(IntList(intArrayOf(1, 3, 7))))
        val clause = r.propagateUnit(5)
        assertTrue(clause is Conjunction)
        assertContentEquals(intArrayOf(0, 2, 6), clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagateNegUnitNegatesClauseConjunction() {
        val r = Reified(4, Conjunction(IntList(intArrayOf(1, 3, 7))))
        val clause = r.propagateUnit(5)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(0, 2, 6), clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagatePosUnitConjunction() {
        val r = Reified(2, Conjunction(IntList(intArrayOf(0, 4, 6, 8))))
        val s = r.propagateUnit(8)
        assertContentEquals(intArrayOf(0, 4, 6), (s as Reified).clause.literals.toArray().apply { sort() })
    }

    @Test
    fun propagatePosUnitDisjunction() {
        val r = Reified(2, Disjunction(IntList(intArrayOf(0, 4, 6, 8))))
        val s = r.propagateUnit(8)
        assertContentEquals(intArrayOf(2), (s as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastNegConjunction() {
        val r = Reified(0, Conjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(5).propagateUnit(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastPosConjunction() {
        val r = Reified(0, Conjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(2).propagateUnit(4)
        assertContentEquals(intArrayOf(0), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastNegDisjunction() {
        val r = Reified(0, Disjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(5).propagateUnit(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun propagateLastPosDisjunction() {
        val r = Reified(0, Disjunction(IntList(intArrayOf(2, 4))))
        val c = r.propagateUnit(2).propagateUnit(4)
        assertContentEquals(intArrayOf(0), (c as Conjunction).literals.toArray().apply { sort() })
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Reified(0, Conjunction(IntList(intArrayOf(2, 4, 6, 8)))),
                Reified(2, Conjunction(IntList(intArrayOf(0, 4, 6, 8)))),
                Reified(0, Disjunction(IntList(intArrayOf(2, 4, 6, 8)))),
                Reified(2, Disjunction(IntList(intArrayOf(0, 4, 6, 8))))))
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
