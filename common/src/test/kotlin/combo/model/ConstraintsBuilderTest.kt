package combo.model

import combo.sat.BitArray
import combo.sat.Empty
import combo.sat.Problem
import combo.sat.Tautology
import combo.sat.constraints.Conjunction
import combo.sat.constraints.Disjunction
import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstraintsBuilderTest {

    private val vars = Array(6) { Flag("$it", true) }
    private val index = VariableIndex("Root")

    init {
        vars.forEach { index.add(it) }
    }

    @Test
    fun conjunctionEmpty() {
        with(ConstraintBuilder(index)) {
            assertTrue(conjunction() is Tautology)
        }
    }

    @Test
    fun conjunction() {
        with(ConstraintBuilder(index)) {
            val con = conjunction(vars[0], vars[1], !vars[3]) as Conjunction
            assertContentEquals(intArrayOf(-4, 1, 2), con.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun conjunctionNegated() {
        with(ConstraintBuilder(index)) {
            val con = conjunction(!vars[3], vars[2], vars[4]).not() as Disjunction
            assertContentEquals(intArrayOf(-5, -3, 4), con.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun disjunction() {
        with(ConstraintBuilder(index)) {
            val dis = disjunction(vars[0], vars[1], !vars[3])
            assertContentEquals(intArrayOf(-4, 1, 2), dis.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun disjunctionEmpty() {
        with(ConstraintBuilder(index)) {
            assertTrue(disjunction() is Empty)
        }
    }

    @Test
    fun disjunctionNegated() {
        with(ConstraintBuilder(index)) {
            val dis = disjunction(!vars[3], vars[2], vars[4]).not()
            assertContentEquals(intArrayOf(-5, -3, 4), dis.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun disjunctionDuplicationHandled() {
        with(ConstraintBuilder(index)) {
            val c = (vars[0] or vars[0]) as Disjunction
            assertEquals(1, c.literals.size)
        }
    }

    @Test
    fun and1() {
        with(ConstraintBuilder(index)) {
            val con = (vars[0] and vars[2] and !vars[3]) as Conjunction
            assertContentEquals(intArrayOf(-4, 1, 3), con.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun and2() {
        with(ConstraintBuilder(index)) {
            val con1 = ("1" and "2") as Conjunction
            assertContentEquals(intArrayOf(2, 3), con1.literals.toArray().apply { sort() })
            val con2 = (vars[1] and "2") as Conjunction
            assertContentEquals(intArrayOf(2, 3), con2.literals.toArray().apply { sort() })
            val con3 = ("2" and !vars[0]) as Conjunction
            assertContentEquals(intArrayOf(-1, 3), con3.literals.toArray().apply { sort() })
            val con4 = (vars[1] and !vars[0]) as Conjunction
            assertContentEquals(intArrayOf(-1, 2), con4.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun or1() {
        with(ConstraintBuilder(index)) {
            val dis = (vars[0] or vars[1] or !vars[3]) as Disjunction
            assertContentEquals(intArrayOf(-4, 1, 2), dis.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun or2() {
        with(ConstraintBuilder(index)) {
            val dis1 = ("1" or "2") as Disjunction
            assertContentEquals(intArrayOf(2, 3), dis1.literals.toArray().apply { sort() })
            val dis2 = (vars[1] or "2") as Disjunction
            assertContentEquals(intArrayOf(2, 3), dis2.literals.toArray().apply { sort() })
            val dis3 = ("2" or !vars[0]) as Disjunction
            assertContentEquals(intArrayOf(-1, 3), dis3.literals.toArray().apply { sort() })
            val dis4 = (vars[1] or !vars[0]) as Disjunction
            assertContentEquals(intArrayOf(-1, 2), dis4.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun xor1() {
        with(ConstraintBuilder(index)) {
            val c = (vars[0] xor vars[2] xor vars[1]) as CNF
            val p = Problem(c.disjunctions.toTypedArray(), 3)
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b111 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b011 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b101 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b001 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b110 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b010 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b100 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b000 })))
        }
    }


    @Test
    fun xor2() {
        with(ConstraintBuilder(index)) {
            val c1 = (vars[0] xor !vars[1]) as CNF
            assertContentEquals(intArrayOf(-2, 1), c1.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-1, 2), c1.disjunctions[1].literals.toArray().apply { sort() })
            val c2 = ("2" xor !vars[1]) as CNF
            assertContentEquals(intArrayOf(-2, 3), c2.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-3, 2), c2.disjunctions[1].literals.toArray().apply { sort() })
            val c3 = (vars[3] xor "1") as CNF
            assertContentEquals(intArrayOf(2, 4), c3.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-4, -2), c3.disjunctions[1].literals.toArray().apply { sort() })
            val c4 = ("3" xor "1") as CNF
            assertContentEquals(intArrayOf(2, 4), c4.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-4, -2), c4.disjunctions[1].literals.toArray().apply { sort() })
        }
    }

    @Test
    fun implies1() {
        // ((a implies b) implies c)
        with(ConstraintBuilder(index)) {
            val c = (vars[0] implies vars[1] implies vars[2]) as CNF
            val p = Problem(c.disjunctions.toTypedArray(), 3)
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b111 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b011 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b101 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b001 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b100 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b010 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b100 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b000 })))
        }
    }

    @Test
    fun implies2() {
        with(ConstraintBuilder(index)) {
            val c1 = (vars[0] implies !vars[1]) as Disjunction
            assertContentEquals(intArrayOf(-2, -1), c1.literals.toArray().apply { sort() })
            val c2 = ("3" implies vars[1]) as Disjunction
            assertContentEquals(intArrayOf(-4, 2), c2.literals.toArray().apply { sort() })
            val c3 = ("3" implies "4") as Disjunction
            assertContentEquals(intArrayOf(-4, 5), c3.literals.toArray().apply { sort() })
            val c4 = (!vars[3] implies "4") as Disjunction
            assertContentEquals(intArrayOf(4, 5), c4.literals.toArray().apply { sort() })
        }
    }

    @Test
    fun equivalent1() {
        // ((a equivalent b) equivalent c)
        with(ConstraintBuilder(index)) {
            val c = (vars[0] equivalent vars[1] equivalent vars[2]) as CNF
            val p = Problem(c.disjunctions.toTypedArray(), 3)
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b111 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b011 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b101 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b001 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b110 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b010 })))
            assertTrue(p.satisfies(BitArray(3, IntArray(1) { 0b100 })))
            assertFalse(p.satisfies(BitArray(3, IntArray(1) { 0b000 })))
        }
    }

    @Test
    fun equivalent2() {
        with(ConstraintBuilder(index)) {
            val c1 = (vars[0] equivalent !vars[1]) as CNF
            assertContentEquals(intArrayOf(-2, -1), c1.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(1, 2), c1.disjunctions[1].literals.toArray().apply { sort() })
            val c2 = ("3" equivalent vars[1]) as CNF
            assertContentEquals(intArrayOf(-4, 2), c2.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-2, 4), c2.disjunctions[1].literals.toArray().apply { sort() })
            val c3 = ("3" equivalent "4") as CNF
            assertContentEquals(intArrayOf(-4, 5), c3.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-5, 4), c3.disjunctions[1].literals.toArray().apply { sort() })
            val c4 = (!vars[3] equivalent "4") as CNF
            assertContentEquals(intArrayOf(4, 5), c4.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-5, -4), c4.disjunctions[1].literals.toArray().apply { sort() })
        }
    }

    @Test
    fun excludes() {
        with(ConstraintBuilder(index)) {
            val c = excludes(vars[0], !vars[1])
            val p = Problem(arrayOf(c), 2)
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b11))))
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b01))))
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b10))))
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b00))))
        }
    }

    @Test
    fun exactly() {
        with(ConstraintBuilder(index)) {
            val c = exactly(2, arrayOf(vars[0], !vars[1]))
            val p = Problem(arrayOf(c), 2)
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b11))))
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b01))))
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b10))))
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b00))))
        }
    }

    @Test
    fun atMost() {
        with(ConstraintBuilder(index)) {
            val c = atMost(1, arrayOf(!vars[1], vars[0]))
            val p = Problem(arrayOf(c), 2)
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b11))))
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b01))))
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b10))))
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b00))))
        }
    }

    @Test
    fun atLeast() {
        with(ConstraintBuilder(index)) {
            val c = atLeast(2, arrayOf(vars[0], !vars[1]))
            val p = Problem(arrayOf(c), 2)
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b11))))
            assertTrue(p.satisfies(BitArray(2, intArrayOf(0b01))))
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b10))))
            assertFalse(p.satisfies(BitArray(2, intArrayOf(0b00))))
        }
    }

    @Test
    fun reifiedEquivalent1() {
        with(ConstraintBuilder(index)) {
            val c = vars[0] reifiedEquivalent conjunction(vars[1], !vars[2])
            val p = Problem(arrayOf(c), 3)
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b111))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b011))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b101))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b001))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b110))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b010))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b100))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b000))))
        }
    }

    @Test
    fun reifiedEquivalent2() {
        with(ConstraintBuilder(index)) {
            val c = "0" reifiedEquivalent disjunction(vars[1], vars[2])
            val p = Problem(arrayOf(c), 3)
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b111))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b011))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b101))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b001))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b110))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b010))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b100))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b000))))
        }
    }

    @Test
    fun reifiedEquivalent3() {
        with(ConstraintBuilder(index)) {
            val c = !vars[2] reifiedEquivalent disjunction(vars[1], vars[0])
            val p = Problem(arrayOf(c), 3)
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b111))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b011))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b101))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b001))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b110))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b010))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b100))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b000))))
        }
    }

    @Test
    fun reifiedImplies1() {
        with(ConstraintBuilder(index)) {
            val c = vars[0] reifiedImplies conjunction(vars[1], !vars[2])
            val p = Problem(arrayOf(c), 3)
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b111))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b011))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b101))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b001))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b110))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b010))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b100))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b000))))
        }
    }

    @Test
    fun reifiedImplies2() {
        with(ConstraintBuilder(index)) {
            val c = "0" reifiedImplies disjunction(vars[1], vars[2])
            val p = Problem(arrayOf(c), 3)
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b111))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b011))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b101))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b001))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b110))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b010))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b100))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b000))))
        }
    }

    @Test
    fun reifiedImplies3() {
        with(ConstraintBuilder(index)) {
            val c = !vars[2] reifiedImplies disjunction(vars[1], vars[0])
            val p = Problem(arrayOf(c), 3)
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b111))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b011))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b101))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b001))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b110))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b010))))
            assertTrue(p.satisfies(BitArray(3, intArrayOf(0b100))))
            assertFalse(p.satisfies(BitArray(3, intArrayOf(0b000))))
        }
    }

    @Test
    fun cnf() {
        with(ConstraintBuilder(index)) {
            val c1 = vars[0] or vars[2] or vars[3] or vars[4] or !vars[5]
            val c2 = vars[0] or vars[2]
            val c3 = vars[2] or vars[4]
            val c = (!c1 or (c2 and c3)) as CNF
            val instance = BitArray(6)
            instance[5] = true
            assertTrue(Problem(c.disjunctions.toTypedArray(), 6).satisfies(instance))
        }
    }

    @Test
    fun cnfToConjunction() {
        with(ConstraintBuilder(index)) {
            val c1 = vars[1] and vars[0]
            val c2 = vars[2] and vars[3]
            val c = c1 and c2
            assertTrue(c is Conjunction)
            assertContentEquals(c.literals.toArray().apply { sort() }, intArrayOf(1, 2, 3, 4))
        }
    }

    @Test
    fun cnfConjunctionDisjunction() {
        with(ConstraintBuilder(index)) {
            val c1 = vars[0] and vars[1]
            val c2 = vars[2] or vars[3]
            val c3 = (c1 and c2) as CNF
            assertEquals(3, c3.disjunctions.size)
            assertContentEquals(intArrayOf(1), c3.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(2), c3.disjunctions[1].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(3, 4), c3.disjunctions[2].literals.toArray().apply { sort() })
        }
    }

    @Test
    fun cnfNegatedConjunctionDisjunction() {
        with(ConstraintBuilder(index)) {
            val c1 = vars[0] and vars[1]
            val c2 = vars[2] or vars[3]
            val c3 = c1 and c2
            val neg = !(c3) as CNF
            assertEquals(2, neg.disjunctions.size)
            assertContentEquals(intArrayOf(-4, -2, -1), neg.disjunctions[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-3, -2, -1), neg.disjunctions[1].literals.toArray().apply { sort() })
        }
    }

    @Test
    fun cnfNegatedDisjunctions() {
        with(ConstraintBuilder(index)) {
            val c1 = vars[0] or vars[1]
            val c2 = vars[2] or vars[3]
            val neg = !(c1 and c2) as CNF
            assertEquals(4, neg.disjunctions.size)
            val sents = neg.disjunctions
            assertContentEquals(intArrayOf(-4, -2), sents[0].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-3, -2), sents[1].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-4, -1), sents[2].literals.toArray().apply { sort() })
            assertContentEquals(intArrayOf(-3, -1), sents[3].literals.toArray().apply { sort() })
        }
    }

    @Test
    fun cnfNegatedOredConjunctions() {
        with(ConstraintBuilder(index)) {
            val c1 = vars[0] and vars[1]
            val c2 = vars[2] and vars[3]
            val neg = !(c1 or c2) as CNF
            assertEquals(16, neg.disjunctions.size)
        }
    }

    @Test
    fun cnfLarge() {
        val a = Flag("a", true)
        val b = Flag("b", true)
        val c = Flag("c", true)
        val d = Flag("d", true)
        val e = Flag("e", true)
        val f = Flag("f", true)
        val g = Flag("g", true)
        val index = VariableIndex("").apply {
            add(a); add(b); add(c); add(d); add(e); add(f); add(g)
        }
        with(ConstraintBuilder(index)) {
            val con = ((f and g) or ((a or b or !c) and d and e)) as CNF
            val p = Problem(con.disjunctions.toTypedArray(), 7)

            // Random sample
            assertTrue(p.satisfies(BitArray(7, IntArray(1) { 0b111111 })))
            assertTrue(p.satisfies(BitArray(7, IntArray(1) { 0b111110 })))
            assertFalse(p.satisfies(BitArray(7, IntArray(1) { 0b0101111 })))
            assertFalse(p.satisfies(BitArray(7, IntArray(1) { 0b0000111 })))
            assertTrue(p.satisfies(BitArray(7, IntArray(1) { 0b1110011 })))
            assertTrue(p.satisfies(BitArray(7, IntArray(1) { 0b0011011 })))
        }
    }

    @Test
    fun cnfLargeNegated() {
        val a = Flag("a", true)
        val b = Flag("b", true)
        val c = Flag("c", true)
        val d = Flag("d", true)
        val e = Flag("e", true)
        val f = Flag("f", true)
        val g = Flag("g", true)
        val index = VariableIndex("").apply {
            add(a); add(b); add(c); add(d); add(e); add(f); add(g)
        }
        with(ConstraintBuilder(index)) {
            val con = (f and g) or ((a or b or !c) and d and e)
            val neg = (!con) as CNF
            val p = Problem(neg.disjunctions.toTypedArray(), 7)

            // Random sample
            assertFalse(p.satisfies(BitArray(7, IntArray(1) { 0b111111 })))
            assertFalse(p.satisfies(BitArray(7, IntArray(1) { 0b111110 })))
            assertTrue(p.satisfies(BitArray(7, IntArray(1) { 0b0101111 })))
            assertTrue(p.satisfies(BitArray(7, IntArray(1) { 0b0000111 })))
            assertFalse(p.satisfies(BitArray(7, IntArray(1) { 0b1110011 })))
            assertFalse(p.satisfies(BitArray(7, IntArray(1) { 0b0011011 })))
        }
    }

}
