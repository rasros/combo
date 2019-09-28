package combo.model

import combo.sat.BitArray
import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BoolVarTest {

    @Test
    fun index() {
        val f = Flag("", true)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(1, f.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val f = Flag("", true)
        val instance = BitArray(1)
        assertNull(f.valueOf(instance, 0))
        instance[0] = true
        assertEquals(true, f.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = Flag("f", 1)
        val index = VariableIndex()
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = Flag("f", 1)
        val index = VariableIndex()
        index.add(BitsVar("b", null, 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class OptionTest {

    @Test
    fun missingOptionTest() {
        val o = Multiple("o", null, 1.0, 2.0, 3.0)
        assertFailsWith(IllegalArgumentException::class) {
            o.value(1.2)
        }
    }

    @Test
    fun toLiteral() {
        val a = Nominal("a", null, 1.0, 2.0, 2.5)
        val index = VariableIndex()
        index.add(a)
        assertEquals(3, a.value(2.0).toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val a = Nominal("a", Root(""), 1.0, 2.0, 2.5)
        val index = VariableIndex()
        index.add(a)
        assertEquals(2, a.value(2.0).toLiteral(index))
    }

    @Test
    fun toLiteralNot() {
        val a = Nominal("a", null, 1.0, 2.0, 3.0)
        val index = VariableIndex()
        index.add(a)
        val o = a.value(1.0).not()
        assertEquals(a, o.canonicalVariable)
        assertEquals(a, a.reifiedValue)
        assertEquals(-2, o.toLiteral(index))
    }

    @Test
    fun toLiteralMandatoryNot() {
        val a = Nominal("a", Root(""), 1.0, 2.0, 3.0)
        val index = VariableIndex()
        index.add(a)
        val o = a.value(1.0).not()
        assertEquals(a, o.canonicalVariable)
        assertEquals(-1, o.toLiteral(index))
    }
}

class NominalTest {

    @Test
    fun index() {
        val a = Nominal("a", null, 1, 2, 3)
        val index = VariableIndex()
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(4, a.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val a = Nominal("a", Root(""), 1, 2, 3)
        val index = VariableIndex()
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(3, a.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val a = Nominal("a", null, "a", "b", "c", "d")
        val instance = BitArray(5)
        assertNull(a.valueOf(instance, 0))
        instance[0] = true
        assertFailsWith(IllegalStateException::class) {
            a.valueOf(instance, 0)
        }
        instance[2] = true
        assertEquals("b", a.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatory() {
        val a = Nominal("a", Root(""), "a", "b", "c", "d")
        val instance = BitArray(4)
        assertNull(a.valueOf(instance, 0))
        instance[1] = true
        assertEquals("b", a.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = Nominal("a", null, "a", "b", "c")
        val index = VariableIndex()
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val f = Nominal("a", Root(""), "a", "b", "c")
        val index = VariableIndex()
        index.add(f)
        assertFailsWith(Exception::class) { f.toLiteral(index) }
    }

    @Test
    fun toLiteral2() {
        val f = Nominal("", null, 1, 2, 3, 4, 5, 6, 7)
        val index = VariableIndex()
        index.add(BitsVar("b", null, 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class MultipleTest {

    @Test
    fun index() {
        val a = Multiple("a", null, 1, 2, 3)
        val index = VariableIndex()
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(4, a.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val a = Multiple("a", Root(""), 1, 2, 3)
        val index = VariableIndex()
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(3, a.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val a = Multiple("a", null, "a", "b", "c", "d")
        val instance = BitArray(5)
        assertNull(a.valueOf(instance, 0))
        instance[0] = true
        assertFailsWith(IllegalStateException::class) {
            a.valueOf(instance, 0)
        }
        instance[2] = true
        assertContentEquals(listOf("b"), a.valueOf(instance, 0)!!)
        instance[1] = true
        assertContentEquals(listOf("a", "b"), a.valueOf(instance, 0)!!)
        instance[3] = true
        assertContentEquals(listOf("a", "b", "c"), a.valueOf(instance, 0)!!)
    }

    @Test
    fun valueOfMandatory() {
        val a = Multiple("a", Root(""), "a", "b", "c", "d")
        val index = VariableIndex()
        index.add(a)
        val instance = BitArray(4)
        assertNull(a.valueOf(instance, 0))
        instance[1] = true
        assertContentEquals(listOf("b"), a.valueOf(instance, 0)!!)
        instance[0] = true
        assertContentEquals(listOf("a", "b"), a.valueOf(instance, 0)!!)
        instance[2] = true
        assertContentEquals(listOf("a", "b", "c"), a.valueOf(instance, 0)!!)
    }

    @Test
    fun toLiteral() {
        val f = Multiple("a", null, "a", "b", "c")
        val index = VariableIndex()
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val f = Multiple("a", Root(""), "a", "b", "c")
        val index = VariableIndex()
        index.add(f)
        assertFailsWith(Exception::class) { assertEquals(Int.MAX_VALUE, f.toLiteral(index)) }
    }

    @Test
    fun toLiteral2() {
        val f = Multiple("", null, 1, 2, 3, 4, 5, 6, 7)
        val index = VariableIndex()
        index.add(BitsVar("b", null, 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

