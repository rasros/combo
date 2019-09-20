package combo.sat

import combo.model.VariableIndex
import combo.util.*
import kotlin.math.min
import kotlin.random.Random

interface VectorMapping {
    val binaryIx: Int
    val vectorIx: Int

    val binarySize: Int
    val vectorSize: Int get() = binarySize

    /**
     * The reifiedLiteral decides if a value is missing. If it is 0, then the value is never missing. If the
     * [indicatorVariable] is true then the literal is within the range defined by [binaryIx] and [binarySize] in dimacs
     * format.
     */
    val reifiedLiteral: Int

    /**
     * If indicatorVariable is false then the variable has an indicator variable at the first index.
     */
    val indicatorVariable: Boolean
}

interface IntMapping : VectorMapping {
    val min: Int
    val max: Int
}

interface FloatMapping : VectorMapping {
    val min: Float
    val max: Float
}

class BitsMapping(override val binaryIx: Int,
                  override val vectorIx: Int,
                  override val binarySize: Int,
                  override val reifiedLiteral: Int,
                  override val indicatorVariable: Boolean) : VectorMapping {
    override fun toString() = "BitsMapping($binaryIx)"
}

/**
 * The mapping function should produce a mapping for a variable with the specified binary and vector index. The function
 * will only be called once for each variable.
 */
interface MappingFunction<V : VectorMapping> {
    /**
     * @param binaryIx the starting index of the mapping in the binary format.
     * @param vectorIx the starting index of the mapping in the vector format.
     * @param scopedIndex the index where the variable is declared.
     */
    fun map(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex): V
}

interface Encoder<V : VectorMapping> {

    fun encode(mapping: V, vector: FloatArray, instance: Instance) {
        with(mapping) {
            if (reifiedLiteral != 0 && reifiedLiteral !in instance) {
                if (indicatorVariable)
                // Turn off indicator variable
                    vector[vectorIx] = -1.0f
            } else {
                for (i in 0 until binarySize) {
                    if (instance[binaryIx + i]) vector[vectorIx + i] = 1.0f
                    else vector[vectorIx + i] = -1.0f
                }
            }
        }
    }

    /**
     * The default split adds all available options, since they are independently modelled.
     */
    fun nextSplit(mapping: V, units: IntCollection, rng: Random, maxSplits: Int): IntArray {
        with(mapping) {
            if (!reifiedLiteral in units)
                return EMPTY_INT_ARRAY // The entire variable is excluded
            val all = IntRangeCollection(binaryIx, binaryIx + binarySize).mutableCopy(nullValue = -1)
            for (lit in units)
                all.remove(lit.toIx())
            val arr = IntArray(min(maxSplits, all.size))
            val itr = all.iterator()
            for (i in 0 until arr.size) {
                arr[i] = itr.nextInt()
            }
            return arr
        }
    }
}

interface ZeroOneEncoder<V:VectorMapping> : Encoder<V> {
    override fun encode(mapping: V, vector: FloatArray, instance: Instance) {
        with(mapping) {
            if (reifiedLiteral == 0 || reifiedLiteral in instance) {
                for (i in 0 until binarySize) {
                    if (instance[binaryIx + i]) vector[vectorIx + i] = 1.0f
                }
            }
        }
    }
}

data class VariableEncoder<V : VectorMapping>(val name: String, val mapping: V, val encoder: Encoder<V>) {

    fun encode(vector: FloatArray, instance: Instance) =
            encoder.encode(mapping, vector, instance)

    fun nextSplit(units: IntCollection, rng: Random, maxSplits: Int) = encoder.nextSplit(mapping, units, rng, maxSplits)
}

object BitsEncoder : Encoder<VectorMapping> {
    override fun toString() = "BitsEncoder"
}

object VoidEncoder : Encoder<VectorMapping> {
    override fun toString() = "VoidEncoder"
    override fun encode(mapping: VectorMapping, vector: FloatArray, instance: Instance) {
    }

    override fun nextSplit(mapping: VectorMapping, units: IntCollection, rng: Random, maxSplits: Int) = EMPTY_INT_ARRAY
}

object IntEncoder : Encoder<IntMapping> {

    override fun encode(mapping: IntMapping, vector: FloatArray, instance: Instance) {
        /*{
            if (!zeroOne && indicatorVariable && reifiedLiteral !in instance) {
                // Turn off indicator variable
                vector[vectorIx] = -1.0f
            } else {
                for (i in 0 until binarySize) {
                    if (instance[binaryIx + i]) vector[vectorIx + i] = 1.0f
                    else if (!zeroOne) vector[vectorIx + i] = -1.0f
                }
            }
        }*/
        TODO()
        val zeroOne = false
        with(mapping) {
            val offset: Int
            val intSize: Int
            if (indicatorVariable) {
                offset = 1
                intSize = binarySize - 1
                if (reifiedLiteral in instance) {
                    vector[vectorIx] = 1.0f
                } else {
                    if (!zeroOne) vector[vectorIx] = -1.0f
                    return
                }
            } else {
                offset = 0
                intSize = binarySize
            }

            val v = if (min < 0) instance.getSignedInt(binaryIx + offset, intSize)
            else instance.getBits(binaryIx + offset, intSize)
            vector[vectorIx + offset] = 2 * (v - min) / (max - min).toFloat() - 1.0f
        }
    }

    override fun nextSplit(mapping: IntMapping, units: IntCollection, rng: Random, maxSplits: Int): IntArray {
        TODO()
        with(mapping) {

            if (reifiedLiteral != 0) {
                if (!reifiedLiteral in units) return EMPTY_INT_ARRAY
                else if (reifiedLiteral !in units) return intArrayOf(reifiedLiteral.toIx())
            }

            // All bits that are fixed will be zero
            var unset = -1 ushr (32 - (Int.bsr(min xor max) + binarySize))

            for (lit in units) {
                val ix = lit.toIx() - binaryIx
                if (ix in 0..31) {
                    if (((unset ushr ix) and 1) == 0 && min ushr ix != (if (lit.toBoolean()) 1 else 0))
                        return EMPTY_INT_ARRAY // Contradiction, the forced min/max bit does not equal v
                    unset = unset and (1 shl ix).inv()
                }
            }
            return intArrayOf(binaryIx + Int.bsr(unset))
        }
    }

    override fun toString() = "IntEncoder"
}

object FloatEncoder : Encoder<FloatMapping> {

    override fun encode(mapping: FloatMapping, vector: FloatArray, instance: Instance) {
        TODO()
        with(mapping) {
            val v = instance.getSignedInt(binaryIx, binarySize)
            vector[vectorIx] = 2 * (v - min) / (max - min) - 1.0f
        }
    }

    /**
     * Returns the most significant bit that sufficiently makes the constraint active. This can fail if the constraint
     * is unsatisfiable by returning -1, it may also unknowingly return a positive index even if the constraint is
     * unsatisfiable.
     */
    override fun nextSplit(mapping: FloatMapping, units: IntCollection, rng: Random, maxSplits: Int): IntArray {
        TODO()
        with(mapping) {

            if (reifiedLiteral != 0) {
                if (!reifiedLiteral in units) return EMPTY_INT_ARRAY
                else if (reifiedLiteral !in units) return intArrayOf(reifiedLiteral.toIx())
            }

            // All bits that are fixed will be zero
            var unset = -1 ushr (32 - (Int.bsr(min.toRawBits() xor max.toRawBits()) + 1))

            for (lit in units) {
                val ix = lit.toIx() - binaryIx
                if (ix in 0..31) {
                    if (((unset ushr ix) and 1) == 0 && min.toRawBits() ushr ix != (if (lit.toBoolean()) 1 else 0))
                        return EMPTY_INT_ARRAY // Contradiction, the forced min/max bit does not equal v
                    unset = unset and (1 shl ix).inv()
                }
            }
            return if (unset == 0) EMPTY_INT_ARRAY
            else intArrayOf(binaryIx + Int.bsr(unset))
        }
    }

    override fun toString() = "FloatEncoder"
}

object OrdinalEncoder : Encoder<VectorMapping> {
    override fun encode(mapping: VectorMapping, vector: FloatArray, instance: Instance) {
        TODO()
        with(mapping) {
            if (reifiedLiteral != 0 && reifiedLiteral !in instance) {
                if (indicatorVariable)
                // Turn off indicator variable
                    vector[vectorIx] = -1.0f
            } else {
                val offset = if (indicatorVariable) {
                    vector[vectorIx] = 1.0f
                    1
                } else 0

                val bp = instance.getLast(binaryIx + offset, binaryIx + binarySize)
                if (bp >= 0) {
                    for (i in 0 until bp)
                        vector[offset + vectorIx + i] = 1.0f
                    for (i in bp until binarySize - offset)
                        vector[offset + vectorIx + i] = -1.0f
                }
            }
        }
    }

    override fun toString() = "OrdinalEncoder"
}

object NominalEncoder : Encoder<VectorMapping> {
    override fun encode(mapping: VectorMapping, vector: FloatArray, instance: Instance) {
        with(mapping) {
            if (reifiedLiteral != 0 && reifiedLiteral !in instance) {
                if (indicatorVariable)
                // Turn off indicator variable
                    vector[vectorIx] = -1.0f
            } else {
                val offset = if (indicatorVariable) {
                    vector[vectorIx] = 1.0f
                    1
                } else 0

                for (i in offset until binarySize)
                    vector[vectorIx + i] = -1.0f

                val first = instance.getFirst(binaryIx + offset, binaryIx + binarySize)
                if (first >= 0)
                    vector[vectorIx + offset + first] = 1.0f
            }
        }
    }

    override fun toString() = "NominalEncoder"
}

