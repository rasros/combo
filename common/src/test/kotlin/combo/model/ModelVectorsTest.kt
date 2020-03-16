package combo.model

import combo.model.Model.Companion.model
import combo.sat.BitArray
import combo.sat.optimizers.ExhaustiveSolver
import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectCodedVectorTest {
    @Test
    fun singleMandatory() {
        val m = model { bool() }
        val i0 = BitArray(1, intArrayOf(0))
        val i1 = BitArray(1, intArrayOf(1))
        val mi0 = EffectCodedVector(m, i0)
        val mi1 = EffectCodedVector(m, i1)
        assertContentEquals(floatArrayOf(-1f), mi0.toFloatArray())
        assertContentEquals(floatArrayOf(1f), mi1.toFloatArray())
        assertEquals(-1f, mi0[0])
        assertEquals(1f, mi1[0])
    }

    @Test
    fun singleOptional() {
        val m = model { optionalBits("opt", 1) }

        val i0 = BitArray(2, intArrayOf(0))
        val i1 = BitArray(2, intArrayOf(1))
        val i2 = BitArray(2, intArrayOf(2))
        val i3 = BitArray(2, intArrayOf(3))

        val mi0 = EffectCodedVector(m, i0)
        val mi1 = EffectCodedVector(m, i1)
        val mi2 = EffectCodedVector(m, i2)
        val mi3 = EffectCodedVector(m, i3)

        assertContentEquals(floatArrayOf(-1f, 0f), mi0.toFloatArray())
        assertContentEquals(floatArrayOf(1f, -1f), mi1.toFloatArray())
        assertContentEquals(floatArrayOf(-1f, 0f), mi2.toFloatArray())
        assertContentEquals(floatArrayOf(1f, 1f), mi3.toFloatArray())
    }

    @Test
    fun exhaustive() {
        for (m in TestModels.MODELS) {
            val exhaustiveSolver = ExhaustiveSolver(m.problem)
            val instance = exhaustiveSolver.witnessOrThrow()
            val ecv = EffectCodedVector(m, instance)
            val arr = ecv.toFloatArray()
            for (i in instance.indices) {
                assertEquals(ecv[i], arr[i])
                if (instance.isSet(i)) {
                    assertEquals(1f, ecv[i])
                    assertEquals(1f, arr[i])
                } else {
                    assertTrue(ecv[i] == 0f || ecv[i] == -1f)
                    assertTrue(arr[i] == 0f || arr[i] == -1f)
                }
            }
        }
    }
}
