package combo.sat

import combo.math.IntPermutation
import combo.sat.solvers.ExhaustiveSolver
import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.*

private fun randomExhaustivePropagations(cs: Array<Sentence>) {
    // This test thoroughly test that unit propagation does not change the truth value of a labeling.
    val rng = Random.Default
    for (c in cs) for (i in c.literals) require(i.asIx() <= 4)
    for (l in LabelingPermutation.sequence(5, rng)) {
        for (c in cs) {
            try {
                val c2 = IntPermutation(l.size, rng).iterator().asSequence().fold(c) { s: Sentence, i ->
                    val v = l[i]
                    val cp = s.propagateUnit(i.asLiteral(v))
                    cp.validate()
                    assertEquals(c.satisfies(l), cp.satisfies(l))
                    cp
                }
                assertTrue(c2.isUnit() || c2 == Tautology, "$c + ${l.asLiterals().joinToString()} -> $c2")
            } catch (e: UnsatisfiableException) {
                if (c.satisfies(l))
                    throw e
            }
        }
    }
}

class ConjunctionTest {
    @Test
    fun invalidOrder() {
        assertFailsWith(IllegalArgumentException::class) {
            Conjunction(intArrayOf(2, 0)).validate()
        }
    }

    @Test
    fun satisfiesBlank() {
        val l = BitFieldLabeling(3)
        val s = BitFieldLabeling(3)
        assertTrue(Conjunction(intArrayOf(0, 2, 4)).satisfies(l, s))
        assertEquals(0, Conjunction(intArrayOf(0, 2, 4)).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfies() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertFalse(Conjunction(intArrayOf(0)).satisfies(l))
        assertTrue(Conjunction(intArrayOf(1)).satisfies(l))
        assertFalse(Conjunction(intArrayOf(0, 4)).satisfies(l))
        assertFalse(Conjunction(intArrayOf(0, 5)).satisfies(l))
        assertFalse(Conjunction(intArrayOf(0, 7)).satisfies(l))
        assertTrue(Conjunction(intArrayOf(1, 7)).satisfies(l))
    }

    @Test
    fun flipsToSatisfy() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertEquals(1, Conjunction(intArrayOf(0)).flipsToSatisfy(l))
        assertEquals(0, Conjunction(intArrayOf(1)).flipsToSatisfy(l))
        assertEquals(1, Conjunction(intArrayOf(0, 4)).flipsToSatisfy(l))
        assertEquals(2, Conjunction(intArrayOf(0, 5)).flipsToSatisfy(l))
        assertEquals(1, Conjunction(intArrayOf(0, 7)).flipsToSatisfy(l))
        assertEquals(0, Conjunction(intArrayOf(1, 7)).flipsToSatisfy(l))
    }

    @Test
    fun satisfiesUnset() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1110 })
        assertTrue(Conjunction(intArrayOf(0)).satisfies(l, s))
        assertTrue(Conjunction(intArrayOf(1)).satisfies(l, s))
        assertTrue(Conjunction(intArrayOf(0, 4)).satisfies(l, s))
        assertFalse(Conjunction(intArrayOf(0, 5)).satisfies(l, s))
        assertTrue(Conjunction(intArrayOf(0, 7)).satisfies(l, s))
        assertTrue(Conjunction(intArrayOf(1, 7)).satisfies(l, s))
    }

    @Test
    fun flipsToSatisfyUnset() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1110 })
        assertEquals(0, Conjunction(intArrayOf(0)).flipsToSatisfy(l, s))
        assertEquals(0, Conjunction(intArrayOf(1)).flipsToSatisfy(l, s))
        assertEquals(0, Conjunction(intArrayOf(0, 4)).flipsToSatisfy(l, s))
        assertEquals(1, Conjunction(intArrayOf(0, 5)).flipsToSatisfy(l, s))
        assertEquals(0, Conjunction(intArrayOf(0, 7)).flipsToSatisfy(l, s))
        assertEquals(0, Conjunction(intArrayOf(1, 7)).flipsToSatisfy(l, s))
    }

    @Test
    fun unitPropagationNone() {
        val a = Conjunction(intArrayOf(2, 7))
        val b = a.propagateUnit(9)
        val c = a.propagateUnit(7)
        assertContentEquals(a.literals, b.literals)
        assertContentEquals(a.literals, c.literals)
    }

    @Test
    fun unitPropagationFail1() {
        assertFailsWith(UnsatisfiableException::class) {
            val a = Conjunction(intArrayOf(2, 4))
            a.propagateUnit(3)
            a.propagateUnit(5)
            fail()
        }
    }

    @Test
    fun unitPropagationFail2() {
        assertFailsWith(ValidationException::class) {
            val a = Conjunction(intArrayOf(2, 4))
            a.propagateUnit(5)
        }
    }

    @Test
    fun toCnf() {
        val toCnf = Conjunction(intArrayOf(1, 4)).toCnf().toList()
        assertEquals(2, toCnf.size)
        assertEquals(1, toCnf[0].literals[0])
        assertEquals(4, toCnf[1].literals[0])
    }

    @Test
    fun toCnfSatisfy() {
        val original = Conjunction(intArrayOf(1, 3, 4))
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun generateDimacs() {
        assertEquals("1 0\n4 0\n-5 0", Conjunction(intArrayOf(0, 6, 9)).toDimacs())
        assertEquals("1 0\n2 0\n3 0\n4 0\n-5 0", Conjunction(intArrayOf(0, 2, 4, 6, 9)).toDimacs())
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Conjunction(intArrayOf(3)),
                Conjunction(intArrayOf(0, 3, 8)),
                Conjunction(intArrayOf(0, 2, 4, 6)),
                Conjunction(intArrayOf(2, 4, 6, 8)),
                Conjunction(intArrayOf(2, 4, 7, 9))))
    }
}

class DisjunctionTest {
    @Test
    fun invalidOrder() {
        assertFailsWith(IllegalArgumentException::class) {
            Disjunction(intArrayOf(2, 0)).validate()
        }
    }

    @Test
    fun satisfiesBlank() {
        val l = BitFieldLabeling(3)
        val s = BitFieldLabeling(3)
        assertTrue(Disjunction(intArrayOf(0, 2, 4)).satisfies(l, s))
        assertEquals(0, Disjunction(intArrayOf(0, 2, 4)).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfies() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertFalse(Disjunction(intArrayOf(3, 5)).satisfies(l))
        assertTrue(Disjunction(intArrayOf(3, 1)).satisfies(l))
        assertTrue(Disjunction(intArrayOf(0, 4)).satisfies(l))
        assertFalse(Disjunction(intArrayOf(0, 5)).satisfies(l))
        assertTrue(Disjunction(intArrayOf(0, 7)).satisfies(l))
    }

    @Test
    fun flipsToSatisfy() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        assertEquals(1, Disjunction(intArrayOf(3, 5)).flipsToSatisfy(l))
        assertEquals(0, Disjunction(intArrayOf(3, 1)).flipsToSatisfy(l))
        assertEquals(0, Disjunction(intArrayOf(0, 4)).flipsToSatisfy(l))
        assertEquals(1, Disjunction(intArrayOf(0, 5)).flipsToSatisfy(l))
        assertEquals(0, Disjunction(intArrayOf(0, 7)).flipsToSatisfy(l))
    }

    @Test
    fun satisfiesUnset() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1110 })
        assertFalse(Disjunction(intArrayOf(3, 5)).satisfies(l, s))
        assertTrue(Disjunction(intArrayOf(3, 1)).satisfies(l, s))
        assertTrue(Disjunction(intArrayOf(0, 4)).satisfies(l, s))
        assertTrue(Disjunction(intArrayOf(0, 5)).satisfies(l, s))
        assertTrue(Disjunction(intArrayOf(0, 7)).satisfies(l, s))
    }

    @Test
    fun flipsToSatisfyUnset() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1110 })
        assertEquals(1, Disjunction(intArrayOf(3, 5)).flipsToSatisfy(l, s))
        assertEquals(0, Disjunction(intArrayOf(3, 1)).flipsToSatisfy(l, s))
        assertEquals(0, Disjunction(intArrayOf(0, 4)).flipsToSatisfy(l, s))
        assertEquals(0, Disjunction(intArrayOf(0, 5)).flipsToSatisfy(l, s))
        assertEquals(0, Disjunction(intArrayOf(0, 7)).flipsToSatisfy(l, s))
    }

    @Test
    fun unitPropagation() {
        val a = Disjunction(intArrayOf(0, 2, 4))
        val a1 = a.propagateUnit(2)
        assertEquals(a1, Tautology)
        val a2 = a.propagateUnit(3)
        assertEquals(2, a2.literals.size)
        assertEquals(0, a2.literals[0])
        assertEquals(4, a2.literals[1])

        val b = Disjunction(intArrayOf(1, 2, 4))
        val d1 = b.propagateUnit(1)
        assertEquals(d1, Tautology)
        val d2 = b.propagateUnit(0)
        assertEquals(2, d2.literals.size)
        assertEquals(2, d2.literals[0])
        assertEquals(4, d2.literals[1])
    }

    @Test
    fun unitPropagationNone() {
        val a = Disjunction(intArrayOf(2, 7))
        val b = a.propagateUnit(9)
        assertContentEquals(a.literals, b.literals)
    }

    @Test
    fun unitPropagationFail() {
        assertFailsWith(UnsatisfiableException::class) {
            val a = Disjunction(intArrayOf(2, 5))
            a.propagateUnit(3).propagateUnit(4)
        }
    }

    @Test
    fun toCnf() {
        assertEquals(1, Disjunction(intArrayOf(1, 4)).toCnf().count())
    }

    @Test
    fun toCnfSatisfy() {
        val original = Disjunction(intArrayOf(1, 3, 4))
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toDimacs() {
        assertEquals("1 4 -5 0", Disjunction(intArrayOf(0, 6, 9)).toDimacs())
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Disjunction(intArrayOf(3)),
                Disjunction(intArrayOf(0, 3, 8)),
                Disjunction(intArrayOf(0, 2, 4, 6)),
                Disjunction(intArrayOf(2, 4, 6, 8)),
                Disjunction(intArrayOf(1, 4, 7, 9))))
    }
}

class CardinalityTest {

    @Test
    fun invalidOrder() {
        assertFailsWith(IllegalArgumentException::class) {
            Cardinality(intArrayOf(2, 0)).validate()
        }
    }

    @Test
    fun nonPositiveFail() {
        assertFailsWith(IllegalArgumentException::class) {
            Cardinality(intArrayOf(1), 1, Cardinality.Operator.EXACTLY).validate()
        }
    }

    @Test
    fun degreeUnsatisfiableExactlyFail() {
        assertFailsWith(ValidationException::class) {
            Cardinality(intArrayOf(0, 2), 3, Cardinality.Operator.EXACTLY).validate()
        }
    }

    @Test
    fun degreeUnsatisfiableAtLeastFail() {
        assertFailsWith(ValidationException::class) {
            Cardinality(intArrayOf(0, 2), 3, Cardinality.Operator.AT_LEAST).validate()
        }
    }

    @Test
    fun degreeUnsatisfiableAtMost() {
        Cardinality(intArrayOf(0, 2), 3, Cardinality.Operator.AT_MOST).validate()
    }

    @Test
    fun satisfiesBlank() {
        val l = IntSetLabeling(4)
        val s = IntSetLabeling(4)
        assertTrue(Cardinality(intArrayOf(0, 2, 4)).satisfies(l, s))
        assertEquals(0, Cardinality(intArrayOf(0, 2, 4)).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfies() {
        val e = Cardinality(intArrayOf(0, 2, 4), 1, Cardinality.Operator.AT_MOST)
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
    fun satisfiesUnset() {
        val e = Cardinality(intArrayOf(0, 2, 4), 1, Cardinality.Operator.AT_MOST)
        assertEquals(0, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b000 })))
        assertEquals(0, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertEquals(0, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertEquals(1, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertEquals(0, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertEquals(1, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertEquals(1, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertEquals(1, e.flipsToSatisfy(BitFieldLabeling(3, LongArray(1) { 0b1110 })))
    }

    @Test
    fun flipsToSatisfyUnset() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b1001 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1011 })
        val lits = intArrayOf(0, 2, 4, 6)

        assertEquals(2, Cardinality(lits, 0, Cardinality.Operator.AT_MOST).flipsToSatisfy(l, s))
        assertEquals(1, Cardinality(lits, 1, Cardinality.Operator.AT_MOST).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 2, Cardinality.Operator.AT_MOST).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 3, Cardinality.Operator.AT_MOST).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 4, Cardinality.Operator.AT_MOST).flipsToSatisfy(l, s))

        assertEquals(0, Cardinality(lits, 0, Cardinality.Operator.AT_LEAST).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 1, Cardinality.Operator.AT_LEAST).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 2, Cardinality.Operator.AT_LEAST).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 3, Cardinality.Operator.AT_LEAST).flipsToSatisfy(l, s))
        assertEquals(1, Cardinality(lits, 4, Cardinality.Operator.AT_LEAST).flipsToSatisfy(l, s))

        assertEquals(2, Cardinality(lits, 0, Cardinality.Operator.EXACTLY).flipsToSatisfy(l, s))
        assertEquals(1, Cardinality(lits, 1, Cardinality.Operator.EXACTLY).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 2, Cardinality.Operator.EXACTLY).flipsToSatisfy(l, s))
        assertEquals(0, Cardinality(lits, 3, Cardinality.Operator.EXACTLY).flipsToSatisfy(l, s))
        assertEquals(1, Cardinality(lits, 4, Cardinality.Operator.EXACTLY).flipsToSatisfy(l, s))
    }

    @Test
    fun excludesUnitPropagation() {
        val a = Cardinality(intArrayOf(0, 2, 4), 1, Cardinality.Operator.AT_MOST)
        val a1 = a.propagateUnit(2)
        assertTrue(a1.isUnit())
        assertEquals(2, a1.literals.size)
        assertEquals(1, a1.literals[0])
        assertEquals(5, a1.literals[1])
        val a2 = a.propagateUnit(3)
        assertFalse(a2.isUnit())
        assertEquals(2, a2.literals.size)
        assertEquals(0, a2.literals[0])
        assertEquals(4, a2.literals[1])

        val b = Cardinality(intArrayOf(0, 4), 1, Cardinality.Operator.AT_MOST)
        val b1 = b.propagateUnit(1)
        assertEquals(b1, Tautology)
        val b2 = b.propagateUnit(4)
        assertTrue(b2.isUnit())
        assertEquals(1, b2.literals.size)
    }

    @Test
    fun unitPropagationNone() {
        val a = Cardinality(intArrayOf(2, 8, 10), 1, Cardinality.Operator.AT_MOST)
        assertContentEquals(a.literals, a.propagateUnit(4).literals)
        assertContentEquals(a.literals, a.propagateUnit(6).literals)
        assertContentEquals(a.literals, a.propagateUnit(12).literals)
    }

    @Test
    fun unitPropagationNoneDegree() {
        val a = Cardinality(intArrayOf(2, 6, 8), 4, Cardinality.Operator.AT_LEAST)
        assertContentEquals(a.literals, a.propagateUnit(0).literals)
        assertContentEquals(a.literals, a.propagateUnit(4).literals)
    }

    @Test
    fun unitPropagationAtMost() {
        val a = Cardinality(intArrayOf(2, 4), 1, Cardinality.Operator.AT_MOST)
        val b = a.propagateUnit(2)
        assertContentEquals(intArrayOf(5), (b as Conjunction).literals)
    }

    @Test
    fun unitPropagationFailAtLeast() {
        val a = Cardinality(intArrayOf(2, 4), 1, Cardinality.Operator.AT_LEAST)
        assertFailsWith(ValidationException::class) {
            a.propagateUnit(3).propagateUnit(5)
        }
    }

    @Test
    fun unitPropagationFailDegree() {
        val a = Cardinality(intArrayOf(0, 2, 4, 6), 3, Cardinality.Operator.AT_LEAST)
        assertFailsWith(ValidationException::class) {
            a.propagateUnit(1).propagateUnit(3)
        }
    }

    @Test
    fun unitPropagationDegreeDecrease() {
        val a = Cardinality(intArrayOf(0, 2, 4, 6), 3, Cardinality.Operator.AT_LEAST)
        val b = a.propagateUnit(0)
        assertEquals(2, (b as Cardinality).degree)
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Cardinality(intArrayOf(0, 2, 4, 6, 8), 1, Cardinality.Operator.AT_MOST),
                Cardinality(intArrayOf(0, 2, 4, 6, 8), 3, Cardinality.Operator.AT_MOST),
                Cardinality(intArrayOf(0, 2, 4, 6, 8), 1, Cardinality.Operator.AT_LEAST),
                Cardinality(intArrayOf(0, 2, 4, 6, 8), 3, Cardinality.Operator.AT_LEAST),
                Cardinality(intArrayOf(0, 2, 4, 6, 8), 1, Cardinality.Operator.EXACTLY),
                Cardinality(intArrayOf(0, 2, 4, 6, 8), 3, Cardinality.Operator.EXACTLY)))
    }

    @Test
    fun toCnfSatisfy() {
        val original = Cardinality(intArrayOf(0, 2, 4), 1, Cardinality.Operator.AT_MOST).apply { validate() }
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toCnf() {
        val e = Cardinality(intArrayOf(2, 4, 8, 10), 1, Cardinality.Operator.AT_MOST)
        val c = e.toCnf().toList<Sentence>().toTypedArray()
        assertEquals(6, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 6)).sequence().toSet()
        val s2 = ExhaustiveSolver(Problem(c, 6)).sequence().toSet()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }
}

class ReifiedTest {

    @Test
    fun invalidOrder() {
        assertFailsWith(IllegalArgumentException::class) {
            Reified(4, Disjunction(intArrayOf(2, 0))).validate()
        }
    }

    @Test
    fun literalInReified() {
        assertFailsWith(IllegalArgumentException::class) {
            Reified(2, Disjunction(intArrayOf(2, 4))).validate()
        }
    }

    @Test
    fun negLiteralInReified() {
        assertFailsWith(IllegalArgumentException::class) {
            Reified(3, Disjunction(intArrayOf(2, 4))).validate()
        }
    }

    @Test
    fun satisfiesBlank() {
        val l = ByteArrayLabeling(4)
        val s = ByteArrayLabeling(4)
        assertTrue(Reified(0, Conjunction(intArrayOf(2, 4))).satisfies(l, s))
        assertEquals(0, Reified(0, Conjunction(intArrayOf(2, 4))).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfiedTautology() {
        val d = Reified(0, Tautology)
        assertTrue(d.satisfies(BitFieldLabeling(1).apply { this[0] = true }))
        assertFalse(d.satisfies(BitFieldLabeling(1)))
        assertEquals(0, d.flipsToSatisfy(BitFieldLabeling(1).apply { this[0] = true }))
        assertEquals(1, d.flipsToSatisfy(BitFieldLabeling(1)))
    }

    @Test
    fun satisfiesDisjunction() {
        val d = Reified(1, Disjunction(intArrayOf(2, 5))).apply { validate() }
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
        val d = Reified(1, Disjunction(intArrayOf(2, 5))).apply { validate() }
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
        val d = Reified(0, Conjunction(intArrayOf(2, 5))).apply { validate() }
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
        val d = Reified(0, Conjunction(intArrayOf(2, 5))).apply { validate() }
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
    fun satisfiesConjunctionUnsetClauseOnly() {
        val l = BitFieldLabeling(5, LongArray(1) { 0b10110 })
        val s = BitFieldLabeling(5, LongArray(1) { 0b11110 })
        assertTrue(Reified(8, Conjunction(intArrayOf(0))).satisfies(l, s))
        assertTrue(Reified(9, Conjunction(intArrayOf(0))).satisfies(l, s))
        assertTrue(Reified(8, Conjunction(intArrayOf(1))).satisfies(l, s))
        assertTrue(Reified(9, Conjunction(intArrayOf(1))).satisfies(l, s))
        assertTrue(Reified(8, Conjunction(intArrayOf(0, 4))).satisfies(l, s))
        assertTrue(Reified(9, Conjunction(intArrayOf(0, 4))).satisfies(l, s))
        assertFalse(Reified(8, Conjunction(intArrayOf(0, 5))).satisfies(l, s))
        assertTrue(Reified(9, Conjunction(intArrayOf(0, 5))).satisfies(l, s))
        assertTrue(Reified(8, Conjunction(intArrayOf(0, 7))).satisfies(l, s))
        assertTrue(Reified(9, Conjunction(intArrayOf(0, 7))).satisfies(l, s))
        assertTrue(Reified(8, Conjunction(intArrayOf(1, 7))).satisfies(l, s))
        assertTrue(Reified(9, Conjunction(intArrayOf(1, 7))).satisfies(l, s))
    }

    @Test
    fun flipsToSatisfyConjunctionUnsetClauseOnly() {
        val l = BitFieldLabeling(5, LongArray(1) { 0b10110 })
        val s = BitFieldLabeling(5, LongArray(1) { 0b11110 })
        assertEquals(0, Reified(8, Conjunction(intArrayOf(0))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(9, Conjunction(intArrayOf(0))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(8, Conjunction(intArrayOf(1))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(9, Conjunction(intArrayOf(1))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(8, Conjunction(intArrayOf(0, 4))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(9, Conjunction(intArrayOf(0, 4))).flipsToSatisfy(l, s))
        assertEquals(1, Reified(8, Conjunction(intArrayOf(0, 5))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(9, Conjunction(intArrayOf(0, 5))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(8, Conjunction(intArrayOf(0, 7))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(9, Conjunction(intArrayOf(0, 7))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(8, Conjunction(intArrayOf(1, 7))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(9, Conjunction(intArrayOf(1, 7))).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfiesConjunctionUnsetClauseLiteral() {
        val l = BitFieldLabeling(5, LongArray(1) { 0b01011 })
        val s = BitFieldLabeling(5, LongArray(1) { 0b01111 })
        LabelingPermutation.sequence(4, ByteArrayLabelingBuilder()).forEach { it ->
            val intArray = it.values.mapIndexed { i, v -> i.asLiteral(v == 1.toByte()) }.toIntArray()
            assertTrue(Reified(8, Conjunction(intArray)).satisfies(l, s))
            assertTrue(Reified(9, Conjunction(intArray)).satisfies(l, s))
        }
    }

    @Test
    fun flipsToSatisfyConjunctionUnsetClauseLiteral() {
        val l = BitFieldLabeling(5, LongArray(1) { 0b01011 })
        val s = BitFieldLabeling(5, LongArray(1) { 0b01111 })
        LabelingPermutation.sequence(4, ByteArrayLabelingBuilder()).forEach { it ->
            val intArray = it.values.mapIndexed { i, v -> i.asLiteral(v == 1.toByte()) }.toIntArray()
            assertTrue(Reified(8, Conjunction(intArray)).satisfies(l, s))
            assertTrue(Reified(9, Conjunction(intArray)).satisfies(l, s))
            assertEquals(0, Reified(8, Conjunction(intArray)).flipsToSatisfy(l, s))
            assertEquals(0, Reified(9, Conjunction(intArray)).flipsToSatisfy(l, s))
        }
    }

    @Test
    fun satisfiesDisjunctionUnsetClauseOnly() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1110 })
        assertTrue(Reified(6, Disjunction(intArrayOf(3, 5))).satisfies(l, s))
        assertFalse(Reified(7, Disjunction(intArrayOf(3, 5))).satisfies(l, s))
        assertTrue(Reified(6, Disjunction(intArrayOf(1, 3))).satisfies(l, s))
        assertTrue(Reified(7, Disjunction(intArrayOf(1, 3))).satisfies(l, s))
        assertFalse(Reified(6, Disjunction(intArrayOf(0, 4))).satisfies(l, s))
        assertTrue(Reified(7, Disjunction(intArrayOf(0, 4))).satisfies(l, s))
        assertTrue(Reified(6, Disjunction(intArrayOf(0, 5))).satisfies(l, s))
        assertTrue(Reified(7, Disjunction(intArrayOf(0, 5))).satisfies(l, s))
    }

    @Test
    fun flipsToSatisfyDisjunctionUnsetClauseOnly() {
        val l = BitFieldLabeling(4, LongArray(1) { 0b0110 })
        val s = BitFieldLabeling(4, LongArray(1) { 0b1110 })
        assertEquals(0, Reified(6, Disjunction(intArrayOf(3, 5))).flipsToSatisfy(l, s))
        assertEquals(1, Reified(7, Disjunction(intArrayOf(3, 5))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(6, Disjunction(intArrayOf(1, 3))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(7, Disjunction(intArrayOf(1, 3))).flipsToSatisfy(l, s))
        assertEquals(1, Reified(6, Disjunction(intArrayOf(0, 4))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(7, Disjunction(intArrayOf(0, 4))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(6, Disjunction(intArrayOf(0, 5))).flipsToSatisfy(l, s))
        assertEquals(0, Reified(7, Disjunction(intArrayOf(0, 5))).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfiesDisjunctionUnsetClauseOnly2() {
        val l = BitFieldLabeling(6, LongArray(1) { 0b000000 })
        val s = BitFieldLabeling(6, LongArray(1) { 0b000010 })
        assertTrue(Reified(2, Disjunction(intArrayOf(4, 6, 8, 10))).satisfies(l, s))
        assertEquals(0, Reified(2, Disjunction(intArrayOf(4, 6, 8, 10))).flipsToSatisfy(l, s))
    }

    @Test
    fun satisfiesDisjunctionUnsetClauseLiteral() {
        val l = BitFieldLabeling(5, LongArray(1) { 0b01011 })
        val s = BitFieldLabeling(5, LongArray(1) { 0b01111 })
        LabelingPermutation.sequence(4, ByteArrayLabelingBuilder()).forEach { it ->
            val intArray = it.values.map { it.toInt() }.toIntArray()
            assertTrue(Reified(8, Disjunction(intArray)).satisfies(l, s))
            assertTrue(Reified(9, Disjunction(intArray)).satisfies(l, s))
        }
    }

    @Test
    fun flipsToSatisfyDisjunctionUnsetClauseLiteral() {
        val l = BitFieldLabeling(5, LongArray(1) { 0b01011 })
        val s = BitFieldLabeling(5, LongArray(1) { 0b01111 })
        LabelingPermutation.sequence(4, ByteArrayLabelingBuilder()).forEach { it ->
            val intArray = it.values.map { it.toInt() }.toIntArray()
            assertTrue(Reified(8, Disjunction(intArray)).satisfies(l, s))
            assertTrue(Reified(9, Disjunction(intArray)).satisfies(l, s))
            assertEquals(0, Reified(8, Disjunction(intArray)).flipsToSatisfy(l, s))
            assertEquals(0, Reified(9, Disjunction(intArray)).flipsToSatisfy(l, s))
        }
    }

    @Test
    fun toCnfConjunction() {
        val e = Reified(1, Conjunction(intArrayOf(2, 4)))
        val c = e.toCnf().toList<Sentence>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toSet()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toSet()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfDisjunction() {
        val e = Reified(2, Disjunction(intArrayOf(0, 5)))
        val c = e.toCnf().toList<Sentence>().toTypedArray()
        assertEquals(3, c.size)
        val s1 = ExhaustiveSolver(Problem(arrayOf(e), 3)).sequence().toList()
        val s2 = ExhaustiveSolver(Problem(c, 3)).sequence().toList()
        assertEquals(s1.size, s2.size)
        for (l in s1) assertTrue(s2.contains(l))
    }

    @Test
    fun toCnfSatisfiesDisjunction() {
        val original = Reified(5, Disjunction(intArrayOf(1, 3))).apply { validate() }
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3, Random.Default)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toCnfSatisfiesConjunction() {
        val original = Reified(4, Conjunction(intArrayOf(0, 3))).apply { validate() }
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3, Random.Default)) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun propagateUnitReturnsReifiedClause() {
        val r = Reified(4, Disjunction(intArrayOf(1, 3, 7)))
        val clause = r.propagateUnit(4)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(1, 3, 7), clause.literals)
    }

    @Test
    fun propagateNegUnitNegatesClauseDisjunction() {
        val r = Reified(4, Disjunction(intArrayOf(1, 3, 7)))
        val clause = r.propagateUnit(5)
        assertTrue(clause is Conjunction)
        assertContentEquals(intArrayOf(0, 2, 6), clause.literals)
    }

    @Test
    fun propagateNegUnitNegatesClauseConjunction() {
        val r = Reified(4, Conjunction(intArrayOf(1, 3, 7)))
        val clause = r.propagateUnit(5)
        assertTrue(clause is Disjunction)
        assertContentEquals(intArrayOf(0, 2, 6), clause.literals)
    }

    @Test
    fun propagatePosUnitConjunction() {
        val r = Reified(2, Conjunction(intArrayOf(0, 4, 6, 8)))
        val s = r.propagateUnit(8)
        assertContentEquals(intArrayOf(0, 4, 6), (s as Reified).clause.literals)
    }

    @Test
    fun propagatePosUnitDisjunction() {
        val r = Reified(2, Disjunction(intArrayOf(0, 4, 6, 8)))
        val s = r.propagateUnit(8)
        assertContentEquals(intArrayOf(2), (s as Conjunction).literals)
    }

    @Test
    fun propagateLastNegConjunction() {
        val r = Reified(0, Conjunction(intArrayOf(2, 4)))
        val c = r.propagateUnit(5).propagateUnit(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals)
    }

    @Test
    fun propagateLastPosConjunction() {
        val r = Reified(0, Conjunction(intArrayOf(2, 4)))
        val c = r.propagateUnit(2).propagateUnit(4)
        assertContentEquals(intArrayOf(0), (c as Conjunction).literals)
    }

    @Test
    fun propagateLastNegDisjunction() {
        val r = Reified(0, Disjunction(intArrayOf(2, 4)))
        val c = r.propagateUnit(5).propagateUnit(3)
        assertContentEquals(intArrayOf(1), (c as Conjunction).literals)
    }

    @Test
    fun propagateLastPosDisjunction() {
        val r = Reified(0, Disjunction(intArrayOf(2, 4)))
        val c = r.propagateUnit(2).propagateUnit(4)
        assertContentEquals(intArrayOf(0), (c as Conjunction).literals)
    }

    @Test
    fun randomExhaustivePropagations() {
        randomExhaustivePropagations(arrayOf(
                Reified(0, Conjunction(intArrayOf(2, 4, 6, 8))),
                Reified(2, Conjunction(intArrayOf(0, 4, 6, 8))),
                Reified(0, Disjunction(intArrayOf(2, 4, 6, 8))),
                Reified(2, Disjunction(intArrayOf(0, 4, 6, 8)))))
    }
}

