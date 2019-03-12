package combo.sat.constraints

import combo.model.BitsVar
import combo.model.FeatureIndex
import combo.model.FloatVar
import combo.model.IntVar
import combo.sat.*
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.*

class IntBoundsTest : ConstraintTest() {

    private fun nbrLiterals(min: Int, max: Int) = IntVar("", true, min, max).nbrLiterals

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
    fun randomCoerce() {
        fun testBounds(ix: Int, min: Int, max: Int) {
            for (i in 1..1000) {
                val coercedInstances = randomCoerce(IntBounds(ix, min, max, nbrLiterals(min, max)))
                val feature = IntVar(mandatory = true, min = min, max = max)
                val index = FeatureIndex("")
                if (ix > 0) index.add(BitsVar(mandatory = true, nbrBits = ix))
                index.add(feature)
                val values = coercedInstances.map { feature.valueOf(it, index)!! }
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
        assertTrue(satisfies(-Float.MAX_VALUE, Float.MAX_VALUE, 0.0F))
        assertFalse(satisfies(-Float.MAX_VALUE, Float.MAX_VALUE, Float.NaN))
        assertFalse(satisfies(-Float.MAX_VALUE, Float.MAX_VALUE, Float.POSITIVE_INFINITY))
        assertFalse(satisfies(-Float.MAX_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun randomCoerce() {
        fun testBounds(min: Float, max: Float) {
            for (i in 1..1000) {
                val bounds = FloatBounds(0, min, max)
                val coercedInstances = InstancePermutation(32, BitArrayFactory, Random).asSequence().take(1000).map {
                    bounds.coerce(it, Random)
                    it
                }
                val feature = FloatVar(mandatory = true, min = min, max = max)
                val index = FeatureIndex("")
                index.add(feature)
                val values = coercedInstances.map { feature.valueOf(it, index)!! }.toList()
                for (v in values) {
                    assertTrue(v <= max)
                    assertTrue(v >= min)
                }
                val range = max(1.0F, max - min)
                if ((values.max()!! - max).absoluteValue <= range * 1E-3 &&
                        (values.min()!! - min).absoluteValue <= range * 1E-3) return
            }
            fail("Float bounds not found in coerced instances.")
        }
        testBounds(1.0F, 2.0F)
        testBounds(-10.0F, 20.0F)
        testBounds(0.102F, 0.103F)
        testBounds(-Float.MAX_VALUE, Float.MAX_VALUE)
        testBounds(0.0F, Float.MIN_VALUE)
    }
}
