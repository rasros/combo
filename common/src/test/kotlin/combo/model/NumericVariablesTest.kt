package combo.model

import combo.sat.BitArray
import combo.sat.constraints.Conjunction
import combo.sat.setBits
import combo.sat.setFloat
import combo.test.assertContentEquals
import combo.util.IntHashSet
import combo.util.MAX_VALUE32
import combo.util.MIN_VALUE32
import kotlin.random.Random
import kotlin.test.*

class IntVarTest {

    @Test
    fun minEqMax() {
        assertFailsWith(IllegalArgumentException::class) {
            IntVar(min = 0, max = 0, name = "", parent = Root(""))
        }
    }

    @Test
    fun literalSize() {
        assertEquals(3, IntVar("", Root(""), -4, 0).nbrLiterals)
        assertEquals(3, IntVar("", Root(""), -4, 3).nbrLiterals)
        assertEquals(2, IntVar("", Root(""), 0, 3).nbrLiterals)
        assertEquals(2, IntVar("", Root(""), 0, 2).nbrLiterals)

        assertEquals(5, IntVar("", Root(""), -10, 8).nbrLiterals)
        assertEquals(5, IntVar("", Root(""), -16, 15).nbrLiterals)
        assertEquals(5, IntVar("", Root(""), -10, 15).nbrLiterals)
    }

    @Test
    fun literalSizeBounds() {
        fun limits(v: IntVar) {
            val min = 1
            val max = 32 + if (v.mandatory) 0 else 1
            assertTrue(v.nbrLiterals in min..max)
        }
        limits(IntVar("", Root(""), Int.MIN_VALUE, Int.MAX_VALUE))
        limits(IntVar("", Root(""), Int.MIN_VALUE, 0))
        limits(IntVar("", Root(""), 0, Int.MAX_VALUE))
        limits(IntVar("", Root(""), -1, Int.MAX_VALUE))
        limits(IntVar("", null, Int.MIN_VALUE, Int.MAX_VALUE))
        limits(IntVar("", null, Int.MIN_VALUE, 0))
        limits(IntVar("", null, 0, Int.MAX_VALUE))
        limits(IntVar("", null, -1, Int.MAX_VALUE))
    }

    @Test
    fun index() {
        val f = IntVar("", null, min = 0, max = 1)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(2, f.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val f = IntVar("", Root(""), min = 0, max = 1)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(1, f.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val f = IntVar("", null, min = -1, max = 10)
        val instance = BitArray(f.nbrLiterals)
        assertNull(f.valueOf(instance, 0))
        instance[0] = true
        assertEquals(0, f.valueOf(instance, 0))
        instance.setBits(1, f.nbrLiterals - 1, 4)
        assertEquals(4, f.valueOf(instance, 0))
        for (i in instance.indices) instance[i] = true
        val actual = f.valueOf(instance, 0)
        assertEquals(-1, actual)
    }

    @Test
    fun valueOfMandatory() {
        val f = IntVar("", Root(""), min = -1, max = 10)
        val index = VariableIndex()
        index.add(f)
        val instance = BitArray(f.nbrLiterals)
        assertEquals(0, f.valueOf(instance, 0))
        instance.setBits(0, f.nbrLiterals, 4)
        assertEquals(4, f.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatoryNonZero() {
        val f = IntVar("", Root(""), min = 1, max = 10)
        val index = VariableIndex()
        index.add(f)
        val instance = BitArray(f.nbrLiterals)
        assertEquals(0, f.valueOf(instance, 0))
        instance.setBits(0, f.nbrLiterals, 4)
        assertEquals(4, f.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = IntVar("", null, min = -1000, max = 100)
        val index = VariableIndex()
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = IntVar("", null, min = -12489121, max = -41)
        val index = VariableIndex()
        index.add(BitsVar("b", null, 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class IntLiteralTest {

    @Test
    fun outOfBounds() {
        val v = IntVar("v", null, 1, 20)
        assertFailsWith(IllegalArgumentException::class) {
            v.value(21)
        }
    }

    @Test
    fun toLiteral() {
        fun testToLiteral(min: Int, max: Int, value: Int) {
            val v = IntVar("v", null, min, max)
            val index = VariableIndex()
            index.add(v)
            val instance = BitArray(index.nbrVariables)
            val set = IntHashSet()
            v.value(value).collectLiterals(index, set)
            Conjunction(set).coerce(instance, Random)
            assertEquals(value, v.valueOf(instance, index.indexOf(v)))
        }

        for (i in -10..10) testToLiteral(-10, 10, i)
        for (i in 1..10) testToLiteral(1, 10, i)
        for (i in -1 downTo -10) testToLiteral(-10, -1, i)
        testToLiteral(Int.MIN_VALUE, Int.MAX_VALUE, -1)
        testToLiteral(Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE)
        testToLiteral(Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
    }

    @Test
    fun toLiteralMandatory() {
        val a = IntVar("a", Root(""), 0, 5)
        val index = VariableIndex()
        index.add(a)
        fun test(literals: IntArray, value: Int) {
            val set = IntHashSet()
            a.value(value).collectLiterals(index, set)
            val arr = set.toArray().apply { sort() }
            assertContentEquals(literals.apply { sort() }, arr)
        }
        test(intArrayOf(-1, -2, -3), 0)
        test(intArrayOf(1, -2, -3), 1)
        test(intArrayOf(-1, 2, -3), 2)
        test(intArrayOf(1, 2, -3), 3)
        test(intArrayOf(-1, -2, 3), 4)
        test(intArrayOf(1, -2, 3), 5)
    }
}

class FloatVarTest {

    @Test
    fun minEqMax() {
        assertFailsWith(IllegalArgumentException::class) {
            FloatVar("", Root(""), min = 0F, max = 0F)
        }
    }

    @Test
    fun index() {
        val f = FloatVar("", null, min = 0F, max = 1F)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(33, f.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val f = FloatVar("", parent = Root(""), min = 0F, max = 1F)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(32, f.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val f = FloatVar("", null, min = -1F, max = 10F)
        val instance = BitArray(f.nbrLiterals)
        assertNull(f.valueOf(instance, 0))
        instance[0] = true
        assertEquals(0F, f.valueOf(instance, 0))
        instance.setFloat(1, 4.5F)
        assertEquals(4.5F, f.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatory() {
        val f = FloatVar("", Root(""), min = -1F, max = 10F)
        val instance = BitArray(f.nbrLiterals)
        assertEquals(0F, f.valueOf(instance, 0))
        instance.setFloat(0, 4.5F)
        assertEquals(4.5F, f.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatoryNonZero() {
        val f = FloatVar("", Root(""), min = 1F, max = 10F)
        val instance = BitArray(f.nbrLiterals)
        assertEquals(0F, f.valueOf(instance, 0))
        instance.setFloat(0, 4F)
        assertEquals(4F, f.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = FloatVar("", null, min = -1000.1F, max = 1000.0F)
        val index = VariableIndex()
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }

    @Test
    fun toLiteral2() {
        val f = FloatVar("", null, min = 1F, max = 10F)
        val index = VariableIndex()
        index.add(BitsVar("b", null, 5))
        index.add(f)
        assertEquals(7, f.toLiteral(index))
    }
}

class FloatLiteralTest {

    @Test
    fun outOfBounds() {
        val v = FloatVar("v", null, 1F, 20F)
        assertFailsWith(IllegalArgumentException::class) {
            v.value(21F)
        }
    }

    @Test
    fun nonFinite() {
        val v = FloatVar("v", null, 1F, 20F)
        assertFailsWith(IllegalArgumentException::class) {
            v.value(Float.NaN)
        }
        assertFailsWith(IllegalArgumentException::class) {
            v.value(Float.POSITIVE_INFINITY)
        }
        assertFailsWith(IllegalArgumentException::class) {
            v.value(Float.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun toLiteral() {
        fun testToLiteral(min: Float, max: Float, value: Float) {
            val v = FloatVar("v", null, min, max)
            val index = VariableIndex()
            index.add(v)
            val instance = BitArray(index.nbrVariables)
            val set = IntHashSet()
            v.value(value).collectLiterals(index, set)
            Conjunction(set).coerce(instance, Random)
            assertEquals(value.toBits(), v.valueOf(instance, 0)!!.toBits())
        }

        for (i in -10..10) testToLiteral(-10F, 10F, i.toFloat())
        for (i in 1..10) testToLiteral(0F, 1F, 1F / i.toFloat())
        for (i in -1 downTo -10) testToLiteral(-10F, -1F, i.toFloat())
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, 0F)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, MAX_VALUE32)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, -MAX_VALUE32)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, MIN_VALUE32)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, -MIN_VALUE32)
    }

    @Test
    fun toLiteralMandatory() {
        fun testToLiteral(min: Float, max: Float, value: Float) {
            val v = FloatVar("v", Root(""), min, max)
            val index = VariableIndex()
            index.add(v)
            val instance = BitArray(index.nbrVariables)
            val set = IntHashSet()
            v.value(value).collectLiterals(index, set)
            Conjunction(set).coerce(instance, Random)
            assertEquals(value.toBits(), v.valueOf(instance, 0)!!.toBits())
        }

        for (i in -10..10) testToLiteral(-10F, 10F, i.toFloat())
        for (i in 1..10) testToLiteral(0F, 1F, 1F / i.toFloat())
        for (i in -1 downTo -10) testToLiteral(-10F, -1F, i.toFloat())
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, 0F)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, MAX_VALUE32)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, -MAX_VALUE32)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, MIN_VALUE32)
        testToLiteral(-MAX_VALUE32, MAX_VALUE32, -MIN_VALUE32)
    }
}

class BitsVarTest {

    @Test
    fun noBits() {
        assertFailsWith(IllegalArgumentException::class) {
            BitsVar("", Root(""), 0)
        }
    }

    @Test
    fun index() {
        val f = BitsVar("", null, 1)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(2, f.nbrLiterals)
    }

    @Test
    fun indexMandatory() {
        val f = BitsVar("", Root(""), 1)
        val index = VariableIndex()
        index.add(f)
        assertEquals(0, index.indexOf(f))
        assertEquals(1, f.nbrLiterals)
    }

    @Test
    fun valueOf() {
        val f = BitsVar("", null, 10)
        val instance = BitArray(f.nbrLiterals)
        assertNull(f.valueOf(instance, 0))
        instance[0] = true
        assertEquals(BitArray(10), f.valueOf(instance, 0))
        instance[1] = true
        assertEquals(BitArray(10).apply { this[0] = true }, f.valueOf(instance, 0))
        instance[4] = true
        assertEquals(BitArray(10).apply { this[0] = true; this[3] = true }, f.valueOf(instance, 0))
    }

    @Test
    fun valueOfMandatory() {
        val f = BitsVar("", Root(""), 10)
        val instance = BitArray(f.nbrLiterals)
        assertEquals(BitArray(10), f.valueOf(instance, 0))
        instance[0] = true
        assertEquals(BitArray(10).apply { this[0] = true }, f.valueOf(instance, 0))
        instance[3] = true
        assertEquals(BitArray(10).apply { this[0] = true; this[3] = true }, f.valueOf(instance, 0))
    }

    @Test
    fun toLiteral() {
        val f = BitsVar("", null, 100)
        val index = VariableIndex()
        index.add(f)
        assertEquals(1, f.toLiteral(index))
    }
}

class BitValueTest {

    @Test
    fun outOfBounds() {
        val b = BitsVar("b", null, 10)
        assertFailsWith(IllegalArgumentException::class) {
            b.value(10)
        }
    }

    @Test
    fun toLiteral() {
        val b = BitsVar("b", null, 5)
        val index = VariableIndex()
        index.add(b)
        assertEquals(3, b.value(1).toLiteral(index))
    }

    @Test
    fun toLiteralMandatory() {
        val parent = Root("")
        val b = BitsVar("b", parent, 5)
        val index = VariableIndex()
        index.add(b)
        assertEquals(2, b.value(1).toLiteral(index))
    }

    @Test
    fun toLiteralNot() {
        val b = BitsVar("b", null, 10)
        val v = b.value(2).not()
        val index = VariableIndex()
        index.add(b)
        assertEquals(b, v.canonicalVariable)
        assertEquals(1, b.toLiteral(index))
        assertEquals(2, b.value(0).toLiteral(index))
        assertEquals(-4, v.toLiteral(index))
    }

    @Test
    fun toLiteralMandatoryNot() {
        val b = BitsVar("b", Root(""), 10)
        val v = b.value(9).not()
        val index = VariableIndex()
        index.add(b)
        assertEquals(b, v.canonicalVariable)
        assertEquals(-10, v.toLiteral(index))
    }
}

