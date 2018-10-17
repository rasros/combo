package combo.sat

import combo.math.Rng
import combo.model.UnsatisfiableException
import combo.model.ValidationException
import combo.test.assertContentEquals
import kotlin.test.*

// TODO tests on flipsToSatisfy

class SentenceTest {
    @Test
    fun invalidOrder() {
        assertFailsWith(ValidationException::class) {
            Disjunction(intArrayOf(2, 0)).validate()
        }
    }
}

class ConjunctionTest {

    @Test
    fun satisfiesBlank() {
        val l = BitFieldLabeling(3)
        val s = BitFieldLabeling(3)
        assertTrue(Conjunction(intArrayOf(0, 2, 4)).satisfies(l, s))
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
}

class DisjunctionTest {
    @Test
    fun invalidOrder() {
        assertFailsWith(ValidationException::class) {
            Disjunction(intArrayOf(2, 0)).validate()
        }
    }

    @Test
    fun satisfiesBlank() {
        val l = BitFieldLabeling(3)
        val s = BitFieldLabeling(3)
        assertTrue(Disjunction(intArrayOf(0, 2, 4)).satisfies(l, s))
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
}


class CardinalityTest {

    @Test
    fun invalidOrder() {
        assertFailsWith(ValidationException::class) {
            Cardinality(intArrayOf(2, 0)).validate()
        }
    }

    @Test
    fun nonPositiveFail() {
        assertFailsWith(ValidationException::class) {
            Cardinality(intArrayOf(1), 1, Cardinality.Operator.EXACTLY).validate()
        }
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
    fun satisfiesBlank() {
        val l = SparseLabeling(4)
        val s = SparseLabeling(4)
        assertTrue(Cardinality(intArrayOf(0, 2, 4)).satisfies(l, s))
    }

    @Test
    fun flipsToSatisfySomeUnset() {
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
    fun excludesUnitPropagationNone() {
        val a = Cardinality(intArrayOf(2, 7), 1, Cardinality.Operator.AT_MOST)
        val b = a.propagateUnit(9)
        assertContentEquals(a.literals, b.literals)
    }

    @Test
    fun excludesUnitPropagationFail() {
        val a = Cardinality(intArrayOf(2, 4), 1, Cardinality.Operator.AT_MOST)
        assertFailsWith(ValidationException::class) {
            a.propagateUnit(2).propagateUnit(4)
        }
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
        assertFailsWith(ValidationException::class) {
            Reified(4, Disjunction(intArrayOf(2, 0))).validate()
        }
    }

    @Test
    fun literalInReified() {
        assertFailsWith(ValidationException::class) {
            Reified(2, Disjunction(intArrayOf(2, 4))).validate()
        }
    }

    @Test
    fun negLiteralInReified() {
        assertFailsWith(ValidationException::class) {
            Reified(3, Disjunction(intArrayOf(2, 4))).validate()
        }
    }

    @Test
    fun satisfiesBlank() {
        val l = ByteArrayLabeling(4)
        val s = ByteArrayLabeling(4)
        assertTrue(Reified(0, Conjunction(intArrayOf(2, 4))).satisfies(l, s))
    }

    @Test
    fun satisfiedTautology() {
        val d = Reified(0, Tautology)
        assertTrue(d.satisfies(BitFieldLabeling(1).apply { this[0] = true }))
        assertFalse(d.satisfies(BitFieldLabeling(1)))
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
    fun satisfiesDisjunctionUnsetClauseOnly2() {
        val l = BitFieldLabeling(6, LongArray(1) { 0b000000 })
        val s = BitFieldLabeling(6, LongArray(1) { 0b000010 })
        assertTrue(Reified(2, Disjunction(intArrayOf(4, 6, 8, 10))).satisfies(l, s))
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
        for (l in LabelingPermutation.sequence(3, Rng())) {
            val s1 = original.satisfies(l)
            val s2 = toCnf.asSequence().all { it.satisfies(l) }
            assertEquals(s1, s2)
        }
    }

    @Test
    fun toCnfSatisfiesConjunction() {
        val original = Reified(4, Conjunction(intArrayOf(0, 3))).apply { validate() }
        val toCnf = original.toCnf()
        for (l in LabelingPermutation.sequence(3, Rng())) {
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
}

