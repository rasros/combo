package combo.model

import combo.sat.*
import combo.test.assertContentEquals
import kotlin.test.*

class ConstraintsTest {

    private val vars = Array(6) { flag() }
    private val index = ReferenceIndex(vars)

    @Test
    fun conjunctionBuilder() {
        val cb = vars[0] and vars[1] and vars[3]
        val c = cb.toClause(index)
        assertTrue(c is Conjunction)
        assertContentEquals(intArrayOf(0, 2, 6), c.literals)
    }

    @Test
    fun notConjunction() {
        val c = vars[2] and !vars[3] and vars[4]
        val d = (!c).toClause(index)
        assertTrue(d is Disjunction)
        assertContentEquals(intArrayOf(5, 6, 9), d.literals)
    }

    @Test
    fun disjunctionBuilder() {
        val db = vars[1] or vars[4] or vars[5]
        val d = db.toClause(index)
        assertTrue(d is Disjunction)
        assertContentEquals(intArrayOf(2, 8, 10), d.literals)
    }

    @Test
    fun notDisjunction() {
        val d = vars[4] and !vars[5]
        val c = d.toClause(index)
        assertTrue(c is Conjunction)
        assertContentEquals(intArrayOf(8, 11), c.literals)
    }

    @Test
    fun disjunctionTautology() {
        val db = vars[0] or !vars[0]
        val d = db.toClause(index)
        assertTrue(d === Tautology)
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
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b111 })))
        assertFalse(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b011 })))
        assertFalse(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b101 })))
        assertTrue(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b001 })))
        assertFalse(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertTrue(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b010 })))
        assertTrue(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b100 })))
        assertFalse(p.satisfies(BitFieldLabeling(3, LongArray(1) { 0b000 })))
    }

    @Test
    fun xor2() {
        val c = vars[0] xor !vars[1]
        val sents = c.toSentences(index)
        val p = Problem(sents, 2)
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0))))
    }

    @Test
    fun equivalent1() {
        // ((a equivalent b) equivalent c)
        val c = vars[0] equivalent vars[1] equivalent vars[2]
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun equivalent2() {
        val c = vars[0] equivalent !vars[1]
        val sents = c.toSentences(index)
        val p = Problem(sents, 2)
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0))))
    }

    @Test
    fun implies1() {
        // ((a implies b) implies c)
        val c = vars[0] implies vars[1] implies vars[2]
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun implies2() {
        val c = !vars[0] implies vars[1]
        val sents = c.toSentences(index)
        val p = Problem(sents, 2)
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0))))
    }

    @Test
    fun excludesNegated() {
        assertFailsWith(ValidationException::class) {
            excludes(!vars[0], vars[1]).toSentences(index)
        }
    }

    @Test
    fun excludes1() {
        val c = excludes(vars[0], vars[2], vars[1])
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedConjunction() {
        val c = vars[0] reified and(vars[1], !vars[2])
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedDisjunction() {
        val c = vars[0] reified or(vars[1], vars[2])
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedNegated() {
        val c = !vars[2] reified or(vars[1], vars[0])
        val sents = c.toSentences(index)
        val p = Problem(sents, 3)
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 1, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(1, 0, 0))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 1))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 1, 0))))
        assertTrue(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 1))))
        assertFalse(p.satisfies(ByteArrayLabeling(byteArrayOf(0, 0, 0))))
    }

    @Test
    fun reifiedWithItself() {
        assertFailsWith(ValidationException::class) {
            (vars[0] reified or(!vars[0], vars[1])).toSentences(index)
        }
    }

    @Test
    fun cnf1() {
        //0=f, 1=e, 2=d, 3=c, 4=b, 5=a
        val c1 = vars[0] or vars[2] or vars[3] or vars[4] or !vars[5]
        val c2 = vars[0] or vars[2]
        val c3 = vars[2] or vars[4]
        val c = !c1 or (c2 and c3)
        val sents = c.toSentences(index)
        val l = ByteArrayLabeling(6)
        l[5] = true
        assertTrue(Problem(sents, 6).satisfies(l))
    }

    @Test
    fun alternativeSimpleTest() {
        val a = alternative(1, 2, 3.0, name = "a")
        val b = flag("b")
        val c = a and b
        val index = ReferenceIndex(arrayOf(a, b))
        val sents = c.toSentences(index)
        assertEquals(1, sents.size)
        assertTrue(sents[0] is Conjunction)
        assertContentEquals(intArrayOf(0, 8), sents[0].literals)
    }

    @Test
    fun orSimpleTest() {
        val a = or(1, 2, 3.0, name = "a")
        val b = flag("b")
        val c = a and b
        val index = ReferenceIndex(arrayOf(a, b))
        val sents = c.toSentences(index)
        assertEquals(1, sents.size)
        assertTrue(sents[0] is Conjunction)
        assertContentEquals(intArrayOf(0, 8), sents[0].literals)
    }

    // TODO long and/or cnf examples
    // TODO not on big cnf
}
