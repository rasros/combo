package combo.model

import combo.sat.*
import combo.test.assertContentEquals
import kotlin.test.*

class ConstraintsBuilderTest {

    private val vars = Array(6) { flag() }
    private val index = ReferenceIndex(vars)

    @Test
    fun conjunctionBuilder() {
        val cb = vars[0] and vars[1] and vars[3]
        val c = cb.toClause(index)
        assertTrue(c is Conjunction)
        assertContentEquals(intArrayOf(0, 2, 6), c.literals.toArray().apply { sort() })
    }

    @Test
    fun notConjunction() {
        val c = vars[2] and !vars[3] and vars[4]
        val d = (!c).toClause(index)
        assertTrue(d is Disjunction)
        assertContentEquals(intArrayOf(5, 6, 9), d.literals.toArray().apply { sort() })
    }

    @Test
    fun disjunctionBuilder() {
        val db = vars[1] or vars[4] or vars[5]
        val d = db.toClause(index)
        assertTrue(d is Disjunction)
        assertContentEquals(intArrayOf(2, 8, 10), d.literals.toArray().apply { sort() })
    }

    @Test
    fun notDisjunction() {
        val d = vars[4] and !vars[5]
        val c = d.toClause(index)
        assertTrue(c is Conjunction)
        assertContentEquals(intArrayOf(8, 11), c.literals.toArray().apply { sort() })
    }

    @Test
    fun notDoubleConjunction() {
        val c1 = vars[0] and vars[1]
        val c2 = vars[2] and vars[3]
        val neg = !(c1 and c2)
        val sents = neg.toConstraints(index)
        assertEquals(1, sents.size)
        val d = sents[0] as Disjunction
        assertContentEquals(intArrayOf(1, 3, 5, 7), d.literals.toArray().apply { sort() })
    }

    @Test
    fun notDoubleDisjunction() {
        val c1 = vars[0] or vars[1]
        val c2 = vars[2] or vars[3]
        val neg = !(c1 and c2)
        val sents = neg.toConstraints(index)
        assertEquals(4, sents.size)
        for (sent in sents) assertTrue(sent is Disjunction)
        assertContentEquals(intArrayOf(1, 5), sents[0].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(1, 7), sents[1].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(3, 5), sents[2].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(3, 7), sents[3].literals.toArray().apply { sort() })
    }

    @Test
    fun notDisjunctionAndConjunction() {
        val c1 = vars[0] or vars[1]
        val c2 = vars[2] and vars[3]
        val neg = !(c1 and c2)
        val sents = neg.toConstraints(index)
        assertEquals(2, sents.size)
        for (sent in sents) assertTrue(sent is Disjunction)
        assertContentEquals(intArrayOf(1, 5, 7), sents[0].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(3, 5, 7), sents[1].literals.toArray().apply { sort() })
    }

    @Test
    fun notDoubleOrConjunction() {
        val c1 = vars[0] and vars[1]
        val c2 = vars[2] and vars[3]
        val neg = !(c1 or c2)
        val sents = neg.toConstraints(index)
        assertEquals(16, sents.size)
    }

    @Test
    fun disjunctionTautology() {
        val db = vars[0] or !vars[0]
        val d = db.toClause(index)
        assertSame(d, Tautology)
    }

    @Test
    fun disjunctionDuplicationHandled() {
        val db = vars[0] or vars[0]
        val d = db.toClause(index)
        assertEquals(1, d.literals.size)
    }

    @Test
    fun xor1() {
        val c = vars[0] xor vars[2] xor vars[1]
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b111 })))
        assertFalse(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b011 })))
        assertFalse(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b101 })))
        assertTrue(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b001 })))
        assertFalse(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b110 })))
        assertTrue(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b010 })))
        assertTrue(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b100 })))
        assertFalse(p.satisfies(BitFieldInstance(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun xor2() {
        val c = vars[0] xor !vars[1]
        val sents = c.toConstraints(index)
        val p = Problem(sents, 2)
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0))))
    }

    @Test
    fun equivalent1() {
        // ((a equivalent b) equivalent c)
        val c = vars[0] equivalent vars[1] equivalent vars[2]
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun equivalent2() {
        val c = vars[0] equivalent !vars[1]
        val sents = c.toConstraints(index)
        val p = Problem(sents, 2)
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0))))
    }

    @Test
    fun implies1() {
        // ((a implies b) implies c)
        val c = vars[0] implies vars[1] implies vars[2]
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun implies2() {
        val c = !vars[0] implies vars[1]
        val sents = c.toConstraints(index)
        val p = Problem(sents, 2)
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0))))
    }

    @Test
    fun excludesNegated() {
        assertFailsWith(IllegalArgumentException::class) {
            excludes(!vars[0], vars[1], vars[2]).toConstraints(index)
        }
    }

    @Test
    fun excludes1() {
        val c = excludes(vars[0], vars[2], vars[1])
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedConjunction() {
        val c = vars[0] reified and(vars[1], !vars[2])
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedDisjunction() {
        val c = vars[0] reified or(vars[1], vars[2])
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedNegated() {
        val c = !vars[2] reified or(vars[1], vars[0])
        val sents = c.toConstraints(index)
        val p = Problem(sents, 3)
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 1))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 1))))
        assertFalse(p.satisfies(ByteArrayInstance(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedWithItself() {
        assertFailsWith(IllegalArgumentException::class) {
            (vars[0] reified or(!vars[0], vars[1])).toConstraints(index)
        }
    }

    @Test
    fun cnf1() {
        //0=f, 1=e, 2=d, 3=c, 4=b, 5=a
        val c1 = vars[0] or vars[2] or vars[3] or vars[4] or !vars[5]
        val c2 = vars[0] or vars[2]
        val c3 = vars[2] or vars[4]
        val c = !c1 or (c2 and c3)
        val sents = c.toConstraints(index)
        val instance = ByteArrayInstance(6)
        instance[5] = true
        assertTrue(Problem(sents, 6).satisfies(instance))
    }

    @Test
    fun alternativeSimpleTest() {
        val a = alternative(1, 2, 3.0, name = "a")
        val b = flag("b")
        val c = a and b
        val index = ReferenceIndex(arrayOf(a, b))
        val sents = c.toConstraints(index)
        assertEquals(1, sents.size)
        assertTrue(sents[0] is Conjunction)
        assertContentEquals(intArrayOf(0, 8), sents[0].literals.toArray().apply { sort() })
    }

    @Test
    fun orSimpleTest() {
        val a = multiple(1, 2, 3.0, name = "a")
        val b = flag("b")
        val c = a and b
        val index = ReferenceIndex(arrayOf(a, b))
        val sents = c.toConstraints(index)
        assertEquals(1, sents.size)
        assertTrue(sents[0] is Conjunction)
        assertContentEquals(intArrayOf(0, 8), sents[0].literals.toArray().apply { sort() })
    }

    @Test
    fun largeCnf() {
        val a = flag("a")
        val b = flag("b")
        val c = flag("c")
        val d = flag("d")
        val e = flag("e")
        val f = flag("f")
        val g = flag("g")
        val con = (f and g) or ((a or b or !c) and d and e)
        val index = ReferenceIndex(arrayOf(a, b, c, d, e, f, g))
        val sents = con.toConstraints(index)
        val p = Problem(sents, 7)

        // Random sample
        assertTrue(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b111111 })))
        assertTrue(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b111110 })))
        assertFalse(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b0101111 })))
        assertFalse(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b0000111 })))
        assertTrue(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b1110011 })))
        assertTrue(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b0011011 })))
    }

    @Test
    fun negatedLargeCnf() {
        val a = flag("a")
        val b = flag("b")
        val c = flag("c")
        val d = flag("d")
        val e = flag("e")
        val f = flag("f")
        val g = flag("g")
        val con = (f and g) or ((a or b or !c) and d and e)
        val neg = !con
        val index = ReferenceIndex(arrayOf(a, b, c, d, e, f, g))
        val sents = neg.toConstraints(index)
        val p = Problem(sents, 7)

        // Random sample
        assertFalse(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b111111 })))
        assertFalse(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b111110 })))
        assertTrue(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b0101111 })))
        assertTrue(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b0000111 })))
        assertFalse(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b1110011 })))
        assertFalse(p.satisfies(BitFieldInstance(7, LongArray(1) { 0b0011011 })))
    }
}

class CnfBuilderTest {

    private val vars = Array(6) { flag() }
    private val index = ReferenceIndex(vars)

    @Test
    fun pullIn() {
        // (a or b) and (c or d)
        val cnf1 = CnfBuilder(arrayOf(DisjunctionBuilder(arrayOf(vars[0], vars[1])), DisjunctionBuilder(arrayOf(vars[2], vars[3]))))
        val cnf2 = cnf1.pullIn(DisjunctionBuilder(arrayOf(vars[4], vars[5])))
        val sents1 = cnf1.toConstraints(index)
        val sents2 = cnf2.toConstraints(index)
        assertEquals(2, sents1.size)
        assertEquals(2, sents2.size)
        assertContentEquals(intArrayOf(0, 2), sents1[0].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(4, 6), sents1[1].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(0, 2, 8, 10), sents2[0].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(4, 6, 8, 10), sents2[1].literals.toArray().apply { sort() })
    }

    @Test
    fun pullInDuplication() {
        // (a or b) and (c or d)
        val cnf1 = CnfBuilder(arrayOf(DisjunctionBuilder(arrayOf(vars[0], vars[1])), DisjunctionBuilder(arrayOf(vars[2], vars[3]))))
        val cnf2 = cnf1.pullIn(DisjunctionBuilder(arrayOf(vars[0], vars[2])))
        val sents = cnf2.toConstraints(index)
        assertEquals(2, sents.size)
        assertContentEquals(intArrayOf(0, 2, 4), sents[0].literals.toArray().apply { sort() })
        assertContentEquals(intArrayOf(0, 4, 6), sents[1].literals.toArray().apply { sort() })
    }

    @Test
    fun pullInTautology() {
        // (a or b) and (c or d)
        val cnf1 = CnfBuilder(arrayOf(DisjunctionBuilder(arrayOf(vars[0], vars[1])), DisjunctionBuilder(arrayOf(vars[2], vars[3]))))
        val cnf2 = cnf1.pullIn(DisjunctionBuilder(arrayOf(!vars[0])))
        val sents = cnf2.toConstraints(index)
        assertEquals(1, sents.size)
        assertContentEquals(intArrayOf(1, 4, 6), sents[0].literals.toArray().apply { sort() })
    }

    @Test
    fun distribute() {
        val cnf1 = CnfBuilder(arrayOf(DisjunctionBuilder(arrayOf(vars[0], vars[1])), DisjunctionBuilder(arrayOf(vars[2], vars[3]))))
        val cnf2 = CnfBuilder(arrayOf(
                DisjunctionBuilder(arrayOf(!vars[0], vars[4], vars[5])),
                DisjunctionBuilder(arrayOf(!vars[1], vars[3], vars[5]))))
        val cnf3 = cnf1.distribute(cnf2)
        assertEquals(4, cnf3.disjunctions.size)
        val sents = cnf3.toConstraints(index)
        assertEquals(2, sents.size)
    }
}