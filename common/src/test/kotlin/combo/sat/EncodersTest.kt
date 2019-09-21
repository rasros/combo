package combo.sat

import combo.test.assertContentEquals
import combo.test.assertEquals
import combo.util.*
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class EncoderTestData<V : VectorMapping>(val encoder: VariableEncoder<V>,
                                              val sat: Boolean = true,
                                              val literals: IntCollection = EmptyCollection,
                                              val expectedFirstSplit: Int? = null)

abstract class EncoderTest<V : VectorMapping> {

    abstract val encoders: Array<out EncoderTestData<V>>

    @Test
    fun vectorizeNotOutOfBounds() {
        for (etd in encoders) {
            val (e, _, literals) = etd
            val vecSize = e.mapping.vectorIx + e.mapping.vectorSize
            val instSize = max(e.mapping.reifiedLiteral.toIx(),
                    max(e.mapping.binaryIx + e.mapping.binarySize, literals.map { it.toIx() }.max() ?: 0))
            val vec = FloatArray(vecSize)
            val instance = BitArray(instSize)
            instance.setAll(literals)
            e.encode(vec, instance, zeroOne = true)
            for (i in 0 until vecSize) {
                if (i !in e.mapping.vectorIx..(e.mapping.vectorIx + e.mapping.vectorSize))
                    assertEquals(vec[i], 0.0f, 0.0f)
            }
        }
    }

    @Test
    fun vectorizeZeroOnMissingDataNoZeroOne() {
        for (etd in encoders) {
            val (e, _, literals) = etd
            val vecSize = e.mapping.vectorIx + e.mapping.vectorSize
            val instSize = max(e.mapping.reifiedLiteral.toIx(),
                    max(e.mapping.binaryIx + e.mapping.binarySize, literals.map { it.toIx() }.max() ?: 0))
            val vec = FloatArray(vecSize)
            val instance = BitArray(instSize)
            instance.setAll(literals)
            e.encode(vec, instance, false)
            if (e.mapping.reifiedLiteral == 0) continue
            val offset = if (e.mapping.indicatorVariable) 1 else 0

            if (e.mapping.reifiedLiteral !in instance) {
                // Missing variable, everything 0 except potential indicator variable
                if (e.mapping.indicatorVariable)
                    assertEquals(-1.0f, vec[e.mapping.vectorIx], 0.0f)
                for (i in 0 until e.mapping.vectorSize - offset)
                    assertEquals(0.0f, vec[i + offset + e.mapping.vectorIx], 0.0f)
            } else {
                // No missing variable, indicator should be 1.0f
                if (e.mapping.indicatorVariable)
                    assertEquals(vec[e.mapping.vectorIx], 1.0f, 0.0f)
            }
        }
    }

    @Test
    fun vectorizeZeroOnMissingDataZeroOne() {
        for (etd in encoders) {
            val (e, _, literals) = etd
            val vecSize = e.mapping.vectorIx + e.mapping.vectorSize
            val instSize = max(e.mapping.reifiedLiteral.toIx(),
                    max(e.mapping.binaryIx + e.mapping.binarySize, literals.map { it.toIx() }.max() ?: 0))
            val vec = FloatArray(vecSize)
            val instance = BitArray(instSize)
            instance.setAll(literals)
            e.encode(vec, instance, true)
            if (e.mapping.reifiedLiteral == 0) continue

            if (e.mapping.reifiedLiteral !in instance) {
                // Missing variable, everything 0 including indicator variable
                e.encode(vec, instance, true)
                for (i in 0 until e.mapping.vectorSize)
                    assertEquals(0.0f, vec[i + e.mapping.vectorIx], 0.0f)
            } else {
                // No missing variable, indicator should be 1.0f
                if (e.mapping.indicatorVariable)
                    assertEquals(vec[e.mapping.vectorIx], 1.0f, 0.0f)
            }
        }
    }

    @Test
    fun firstSplit() {
        val rng = Random
        for ((i, e) in encoders.withIndex()) {
            if (e.expectedFirstSplit != null)
                assertContentEquals(
                        intArrayOf(e.expectedFirstSplit), e.encoder.nextSplit(e.literals, rng, 1), "$i")
        }
    }

    @Test
    fun respectMaxSplits() {
        val rng = Random
        for (etd in encoders) {
            val (e, _, literals) = etd
            assertTrue(e.nextSplit(literals, rng, 1).size <= 1)
            assertTrue(e.nextSplit(literals, rng, 2).size <= 2)
            assertTrue(e.nextSplit(literals, rng, 3).size <= 3)
        }
    }

    @Test
    fun dontSplitOnAlreadySplit() {
        val rng = Random
        for (etd in encoders) {
            val (e, _, _) = etd
            if (etd.expectedFirstSplit == null) {
                repeat(100) {
                    val nextSplit = e.nextSplit(etd.literals, rng, Int.MAX_VALUE)
                    for (varIx in nextSplit) {
                        assertTrue(varIx in e.mapping.binaryIx..(e.mapping.binaryIx + e.mapping.binarySize))
                        assertFalse(varIx.toLiteral(true) in etd.literals)
                        assertFalse(varIx.toLiteral(false) in etd.literals)
                    }
                }
            }
        }
    }

    @Test
    fun nextSplitExhaustive() {
        val rng = Random
        for ((i, etd) in encoders.withIndex()) {
            val (e, sat, literals) = etd
            var units = literals.mutableCopy(-1)
            while (true) {
                val splits = e.nextSplit(units, rng, Int.MAX_VALUE)
                if (splits.isEmpty()) break
                else {
                    for (ix in splits) {
                        assertTrue(ix in e.mapping.binaryIx..(e.mapping.binaryIx + e.mapping.binarySize))
                        assertFalse(ix.toLiteral(true) in units, "$splits $units $i $etd")
                        assertFalse(ix.toLiteral(false) in units, "$splits $units $i $etd")
                        units += ix.toLiteral(rng.nextBoolean())
                    }
                }
            }
            assertTrue(!sat || units.isNotEmpty(), "$i $etd")
        }
    }
}

class BitsEncoderTest : EncoderTest<VectorMapping>() {
    override val encoders: Array<EncoderTestData<VectorMapping>> = arrayOf(
            EncoderTestData(VariableEncoder("", BitsMapping(1, 1, 10, 0, false), BitsEncoder), true, collectionOf(8)),
            EncoderTestData(VariableEncoder("", BitsMapping(10, 2, 8, 11, true), BitsEncoder), true, collectionOf(11, -42, -12)),
            EncoderTestData(VariableEncoder("", BitsMapping(10, 2, 8, -1, false), BitsEncoder), true, collectionOf(11, -42, -12)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 1, 10, 100, true), BitsEncoder), false, collectionOf(1)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 10, 1, 0, false), BitsEncoder), false, collectionOf()),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 0, 2, 1, true), BitsEncoder), false, collectionOf(1, 2)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 0, 2, 1, true), BitsEncoder), false, collectionOf(-1)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 10, 100, 1, true), BitsEncoder), false, collectionOf(1, 2, 3, 4, 20, 21, 22, -30)),
            EncoderTestData(VariableEncoder("", BitsMapping(100, 100, 32, -101, true), BitsEncoder), true, IntRangeCollection(101, 122).mutableCopy(0) + collectionOf(130, 132)))
}

class NominalEncoderTest : EncoderTest<VectorMapping>() {
    override val encoders: Array<EncoderTestData<VectorMapping>> = arrayOf(
            EncoderTestData(VariableEncoder("", BitsMapping(1, 1, 10, 0, false), NominalEncoder), true, collectionOf(8)),
            EncoderTestData(VariableEncoder("", BitsMapping(10, 2, 8, 11, true), NominalEncoder), true, collectionOf(11, -42, -12)),
            EncoderTestData(VariableEncoder("", BitsMapping(10, 2, 8, -1, false), NominalEncoder), true, collectionOf(11, -42, -12)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 1, 10, 100, true), NominalEncoder), false, collectionOf(1)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 10, 1, 0, false), NominalEncoder), false, collectionOf()),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 0, 2, 1, true), NominalEncoder), false, collectionOf(1, 2)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 0, 2, 1, true), NominalEncoder), false, collectionOf(-1)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 10, 100, 1, true), NominalEncoder), false, collectionOf(1, 2, 3, 4, 20, 21, 22, -30)),
            EncoderTestData(VariableEncoder("", BitsMapping(100, 100, 32, -101, true), NominalEncoder), true, IntRangeCollection(101, 122).mutableCopy(0) + collectionOf(130, 132)))

    @Test
    fun neverMoreThanOne() {
        for (etd in encoders) {
            val (e, _, literals) = etd
            val vecSize = e.mapping.vectorIx + e.mapping.vectorSize
            val instSize = max(e.mapping.reifiedLiteral.toIx(),
                    max(e.mapping.binaryIx + e.mapping.binarySize, literals.map { it.toIx() }.max() ?: 0))
            val vec = FloatArray(vecSize)
            val instance = BitArray(instSize)
            instance.setAll(literals)
            e.encode(vec, instance, false)
            val offset = if (e.mapping.indicatorVariable) 1 else 0
            var k = 0
            for (i in 0 until e.mapping.vectorSize - offset)
                if (vec[i + offset + e.mapping.vectorIx] == 1.0f)
                    k++
            assertTrue(k <= 1.0f)
        }
    }
}

class OrdinalEncoderTest : EncoderTest<VectorMapping>() {

    override val encoders: Array<EncoderTestData<VectorMapping>> = arrayOf(
            EncoderTestData(VariableEncoder("", BitsMapping(1, 1, 10, 0, false), OrdinalEncoder), true, collectionOf(8)),
            EncoderTestData(VariableEncoder("", BitsMapping(10, 2, 8, 11, true), OrdinalEncoder), true, collectionOf(11, -42, -12)),
            EncoderTestData(VariableEncoder("", BitsMapping(10, 2, 8, -1, false), OrdinalEncoder), true, collectionOf(11, -42, -12)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 1, 10, 100, true), OrdinalEncoder), false, collectionOf(1)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 10, 1, 0, false), OrdinalEncoder), false, collectionOf()),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 0, 2, 1, true), OrdinalEncoder), false, collectionOf(1, 2)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 0, 2, 1, true), OrdinalEncoder), false, collectionOf(-1)),
            EncoderTestData(VariableEncoder("", BitsMapping(0, 10, 100, 1, true), OrdinalEncoder), false, collectionOf(1, 2, 3, 4, 20, 21, 22, -30)),
            EncoderTestData(VariableEncoder("", BitsMapping(100, 100, 32, -101, true), OrdinalEncoder), true, IntRangeCollection(101, 122).mutableCopy(0) + collectionOf(130, 132)))

    @Test
    fun includeLesser() {
        for (etd in encoders) {
            val (e, _, literals) = etd
            val vecSize = e.mapping.vectorIx + e.mapping.vectorSize
            val instSize = max(e.mapping.reifiedLiteral.toIx(),
                    max(e.mapping.binaryIx + e.mapping.binarySize, literals.map { it.toIx() }.max() ?: 0))
            val vec = FloatArray(vecSize)
            val instance = BitArray(instSize)
            instance.setAll(literals)
            if (e.mapping.indicatorVariable && e.mapping.reifiedLiteral !in instance) continue
            val offset = if (e.mapping.indicatorVariable) 1 else 0
            val last = instance.getLast(e.mapping.binaryIx + offset, e.mapping.binaryIx + e.mapping.binarySize)
            e.encode(vec, instance, true)
            var k = 0
            for (i in 0 until e.mapping.vectorSize - offset)
                if (vec[i + offset + e.mapping.vectorIx] == 1.0f)
                    k++
            assertTrue(k <= 1.0f)
        }
    }
}

class FloatEncoderTest {
    @Ignore
    fun todo() {
    }
}
/*
class FloatEncoderTest : EncoderTest<FloatMapping>() {
    override val encoders: Array<EncoderTestData<FloatMapping>> = arrayOf(
            EncoderTestData(VariableEncoder(FloatMapping(1, 1, -1.2f, -1.1f), FloatEncoder), true, collectionOf(33), 21),
            EncoderTestData(VariableEncoder(FloatMapping(10, 2, -Float.MAX_VALUE, Float.MAX_VALUE), FloatEncoder), true, collectionOf(11, -42, -12), 40),
            EncoderTestData(VariableEncoder(FloatMapping(0, 1, 0.0f, Float.MAX_VALUE), FloatEncoder), false, collectionOf(1, 32, -2), -1),
            EncoderTestData(VariableEncoder(FloatMapping(0, 0, 0.0f, 1.0f), FloatEncoder), false, collectionOf(100), 29),
            EncoderTestData(VariableEncoder(FloatMapping(0, 10, -1.0f, 1.0f), FloatEncoder), false, collectionOf(), 31),
            EncoderTestData(VariableEncoder(FloatMapping(0, 10, -3.14f, 3.14f), FloatEncoder), false, collectionOf(1, 2, 3, 4, 20, 21, 22, -30), 31),
            EncoderTestData(VariableEncoder(FloatMapping(100, 100, -3.14f, 3.14f), FloatEncoder), true, IntRangeCollection(101, 122) + collectionOf(130, 132), 130)
    )
}

class IntEncoderTest : EncoderTest<IntMapping>() {
    //test(-2, -1, intArrayOf(32), true)
    //test(-Int.MAX_VALUE, Int.MAX_VALUE, intArrayOf(1, -32, -2), true)
    //test(0, Int.MAX_VALUE, intArrayOf(1, 32, -2), false)
    //test(-3, 3, intArrayOf(2), true)
    //test(-3, 3, intArrayOf(1, 2, 3, -32), false)
    override val encoders: Array<EncoderTestData<IntMapping>> = arrayOf(
            EncoderTestData(VariableEncoder(IntMapping(1, 1, 2, -2, -1), IntEncoder), true, collectionOf(), 2)
    )

    init {
        for (e in encoders) {
            with(e.encoder.mapping) {
                assertEquals(binarySize, IntVar("", true, Root("r"), min, max).nbrLiterals)
            }
        }
    }
}
 */
