package combo.sat.constraints

import combo.model.*
import combo.sat.*
import combo.test.assertEquals
import combo.util.MAX_VALUE32
import combo.util.MIN_VALUE32
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.*

class IntBoundsTest : ConstraintTest() {

    private fun nbrLiterals(min: Int, max: Int) = IntVar("", true, Root(""), min, max).nbrLiterals

    private fun violations(min: Int, max: Int, value: Int): Int {
        val nbrLiterals = nbrLiterals(min, max)
        val instance = BitArray(nbrLiterals)
        if (value < 0) instance.setSignedInt(0, nbrLiterals, value)
        else instance.setBits(0, nbrLiterals, value)
        val bounds = IntBounds(0, min, max, nbrLiterals)
        return bounds.violations(instance)
    }

    @Test
    fun violationsPos() {
        assertEquals(0, violations(2, 5, 5))
        assertEquals(0, violations(0, 5, 0))
        assertEquals(3, violations(0, 100, 105))
        assertEquals(1, violations(0, 4, 5))
    }

    @Test
    fun violationsNeg() {
        assertEquals(0, violations(-10, -4, -4))
        assertEquals(0, violations(-10, -4, -8))
        assertEquals(0, violations(-10, -4, -10))
        assertEquals(1, violations(-10, -4, -3))
        assertEquals(0, violations(-100, 0, 0))
        assertEquals(1, violations(-100, 0, 1))
    }

    @Test
    fun violationsBothPosAndNeg() {
        assertEquals(3, violations(-15, 200, 210))
        assertEquals(1, violations(-15, 200, 201))
        assertEquals(1, violations(-15, 200, -16))
        assertEquals(0, violations(-15, 200, 200))
        assertEquals(0, violations(-15, 200, -15))
        assertEquals(0, violations(-15, 200, 0))
    }

    @Test
    fun updateCache() {
        val nbrLiterals = nbrLiterals(-1, 5)
        val c = IntBounds(0, -1, 5, nbrLiterals)
        for (k in 0 until 16) {
            val instance = BitArray(4, IntArray(1) { k })
            randomCacheUpdates(instance, c)
        }
    }

    @Test
    fun coerce() {
        BitArray(100).also {
            val nbrLiterals = nbrLiterals(-9, 4)
            val c = IntBounds(0, -9, 4, nbrLiterals)
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
            for (i in it.indices) it[i] = true
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
        }
        BitArray(100).also {
            val nbrLiterals = nbrLiterals(Int.MIN_VALUE, 0)
            val c = IntBounds(32, Int.MIN_VALUE, 0, nbrLiterals)
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
            for (i in it.indices) it[i] = true
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
        }
    }

    @Test
    fun randomCoerce() {
        fun testBounds(ix: Int, min: Int, max: Int) {
            for (i in 1..1000) {
                val coercedInstances = randomCoerce(IntBounds(ix, min, max, nbrLiterals(min, max)))
                val variable = IntVar("", mandatory = true, parent = Root(""), min = min, max = max)
                val values = coercedInstances.map { variable.valueOf(it, 0)!! }
                if (min in values && max in values) return
                assertTrue(values.max()!! <= max)
                assertTrue(values.min()!! >= min)
            }
            fail("Int bounds not found in coerced instances.")
        }
        testBounds(1, -1, 2)
        testBounds(0, -1, 2)
        testBounds(3, 0, 1)
        testBounds(1, -7, 7)
        testBounds(0, -15, 15)
        testBounds(1, 0, 15)
        testBounds(1, 0, 9)
        testBounds(0, 0, 9)
    }
}

class FloatBoundsTest : ConstraintTest() {

    private fun satisfies(min: Float, max: Float, value: Float): Boolean {
        val instance = BitArray(32)
        instance.setFloat(0, value)
        val bounds = FloatBounds(0, min, max)
        return bounds.satisfies(instance)
    }

    @Test
    fun satisfies() {
        assertFalse(satisfies(0.0F, 4.0F, -0.0F))
        assertTrue(satisfies(2.0F, 5.0F, 5.0F))
        assertTrue(satisfies(0.0F, 5.0F, 0.0F))
        assertFalse(satisfies(0.0F, 100.0F, 100.1F))
    }

    @Test
    fun satisfiesNonFinite() {
        assertTrue(satisfies(-MAX_VALUE32, MAX_VALUE32, 0.0F))
        assertFalse(satisfies(-MAX_VALUE32, MAX_VALUE32, Float.NaN))
        assertFalse(satisfies(-MAX_VALUE32, MAX_VALUE32, Float.POSITIVE_INFINITY))
        assertFalse(satisfies(-MAX_VALUE32, MAX_VALUE32, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun unitPropagations() {
        fun test(min: Float, max: Float, literals: Literals, sat: Boolean) {
            val fb = FloatBounds(0, min, max)
            val c = fb.unitPropagations(literals)
            if (!sat) assertEquals(c, Empty)
            else if (c is FloatBounds) {
                assertTrue(c.min >= min)
                assertTrue(c.max <= max)
            } else {
                fail("Did not expect: $c")
            }
        }
        test(-1.2f, -1.1f, intArrayOf(32), true)
        test(-Float.MAX_VALUE, Float.MAX_VALUE, intArrayOf(1, -32, -2), true)
        test(0.0f, Float.MAX_VALUE, intArrayOf(1, 32, -2), false)
        test(-3.14f, 3.14f, intArrayOf(1, 2, 3, 4, 20, 21, 22, -30), true)
        test(-3.14f, 3.14f, (1..22).toList().toIntArray() + intArrayOf(30, 31), false)
    }

    @Test
    fun coerce() {
        BitArray(100).also {
            val c = FloatBounds(0, -9.1F, 4.0F)
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
            for (i in it.indices) it[i] = true
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
        }
        BitArray(100).also {
            val c = FloatBounds(32, -MAX_VALUE32, 0.0F)
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
            for (i in it.indices) it[i] = true
            c.coerce(it, Random)
            assertTrue(c.satisfies(it))
        }
    }

    @Test
    fun randomCoerce() {
        fun testBounds(min: Float, max: Float) {
            val bounds = FloatBounds(0, min, max)
            val rng = Random(0)
            val variable = FloatVar("", mandatory = true, min = min, max = max, parent = Root(""))
            val values = InstancePermutation(32, BitArrayBuilder, rng).asSequence().take(100).map {
                bounds.coerce(it, rng)
                variable.valueOf(it, 0)!!
            }.toList().toFloatArray()
            assertEquals(min, values.min()!!, 0.01f * (max - min).absoluteValue)
        }
        testBounds(1.0F, 2.0F)
        testBounds(-10.0F, 20.0F)
        testBounds(0.102F, 0.103F)
        testBounds(-MAX_VALUE32, MAX_VALUE32)
        testBounds(0.0F, MIN_VALUE32)
    }
}
