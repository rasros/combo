package combo.model

import combo.sat.BitArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BoolVarTest {

    @Test
    fun index() {
        val f = Flag("", true)
        val index = VariableIndex("")
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
        val index = VariableIndex("")
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = Flag("f", 1)
        val index = VariableIndex("")
        index.add(BitsVar("b", false, Root(""), 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class OptionTest {

    @Test
    fun missingOptionTest() {
        val o = Multiple("o", false, Root(""), 1.0, 2.0, 3.0)
        assertFailsWith(IllegalArgumentException::class) {
            o.option(1.2)
        }
    }

    @Test
    fun toLiteral() {
        val a = Alternative("a", false, Root(""), 1.0, 2.0, 2.5)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(3, a.option(2.0).toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val a = Alternative("a", true, Root(""), 1.0, 2.0, 2.5)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(2, a.option(2.0).toLiteral(index))
    }

    @Test
    fun toLiteralNot() {
        val a = Alternative("a", false, Root(""), 1.0, 2.0, 3.0)
        val index = VariableIndex("")
        index.add(a)
        val o = a.option(1.0).not()
        assertEquals(a, o.canonicalVariable)
        assertEquals(a, a.reifiedValue)
        assertEquals(-2, o.toLiteral(index))
    }

    @Test
    fun toLiteralMandatoryNot() {
        val a = Alternative("a", true, Root(""), 1.0, 2.0, 3.0)
        val index = VariableIndex("")
        index.add(a)
        val o = a.option(1.0).not()
        assertEquals(a, o.canonicalVariable)
        assertEquals(-1, o.toLiteral(index))
    }
}

class AlternativeTest {

    @Test
    fun index() {
        val a = Alternative("a", false, Root(""), 1, 2, 3)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(4, a.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val a = Alternative("a", true, Root(""), 1, 2, 3)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(3, a.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val a = Alternative("a", false, Root(""), "a", "b", "c", "d")
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
        val a = Alternative("a", true, Root(""), "a", "b", "c", "d")
        val instance = BitArray(4)
        assertNull(a.valueOf(instance, 0))
        instance[1] = true
        assertEquals("b", a.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = Alternative("a", false, Root(""), "a", "b", "c")
        val index = VariableIndex("")
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val f = Alternative("a", true, Root(""), "a", "b", "c")
        val index = VariableIndex("")
        index.add(f)
        assertEquals(Int.MAX_VALUE, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = Alternative("", false, Root(""), 1, 2, 3, 4, 5, 6, 7)
        val index = VariableIndex("")
        index.add(BitsVar("b", false, Root(""), 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class OrdinalTest {

    @Test
    fun index() {
        val a = Ordinal("a", false, Root(""), 1, 2, 3)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(4, a.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val a = Ordinal("a", true, Root(""), 1, 2, 3)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(3, a.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val a = Ordinal("a", false, Root(""), "a", "b", "c", "d")
        val instance = BitArray(5)
        assertNull(a.valueOf(instance, 0))
        instance[0] = true
        assertFailsWith(IllegalStateException::class) {
            a.valueOf(instance, 0)
        }
        instance[1] = true
        instance[2] = true
        assertEquals("b", a.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatory() {
        val a = Ordinal("a", true, Root(""), "a", "b", "c", "d")
        val instance = BitArray(4)
        assertNull(a.valueOf(instance, 0))
        instance[1] = true
        instance[2] = true
        instance[3] = true
        assertEquals("d", a.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = Ordinal("a", false, Root(""), "a", "b", "c")
        val index = VariableIndex("")
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val f = Ordinal("a", true, Root(""), "a", "b", "c")
        val index = VariableIndex("")
        index.add(f)
        assertEquals(Int.MAX_VALUE, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = Ordinal("", false, Root(""), 1, 2, 3, 4, 5, 6, 7)
        val index = VariableIndex("")
        index.add(BitsVar("b", false, Root(""), 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class MultipleTest {

    @Test
    fun index() {
        val a = Multiple("a", false, Root(""), 1, 2, 3)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(4, a.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val a = Multiple("a", true, Root(""), 1, 2, 3)
        val index = VariableIndex("")
        index.add(a)
        assertEquals(0, index.indexOf(a))
        assertEquals(3, a.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val a = Multiple("a", false, Root(""), "a", "b", "c", "d")
        val instance = BitArray(5)
        assertNull(a.valueOf(instance, 0))
        instance[0] = true
        assertFailsWith(IllegalStateException::class) {
            a.valueOf(instance, 0)
        }
        instance[2] = true
        assertEquals(setOf("b"), a.valueOf(instance, 0))
        instance[1] = true
        assertEquals(setOf("a", "b"), a.valueOf(instance, 0))
        instance[3] = true
        assertEquals(setOf("a", "b", "c"), a.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatory() {
        val a = Multiple("a", true, Root(""), "a", "b", "c", "d")
        val index = VariableIndex("")
        index.add(a)
        val instance = BitArray(4)
        assertNull(a.valueOf(instance, 0))
        instance[1] = true
        assertEquals(setOf("b"), a.valueOf(instance, 0))
        instance[0] = true
        assertEquals(setOf("a", "b"), a.valueOf(instance, 0))
        instance[2] = true
        assertEquals(setOf("a", "b", "c"), a.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = Multiple("a", false, Root(""), "a", "b", "c")
        val index = VariableIndex("")
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val f = Multiple("a", true, Root(""), "a", "b", "c")
        val index = VariableIndex("")
        index.add(f)
        assertEquals(Int.MAX_VALUE, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = Multiple("", false, Root(""), 1, 2, 3, 4, 5, 6, 7)
        val index = VariableIndex("")
        index.add(BitsVar("b", false, Root(""), 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

