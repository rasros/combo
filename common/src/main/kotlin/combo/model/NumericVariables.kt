package combo.model

import combo.sat.*
import combo.util.IntHashSet
import combo.util.MAX_VALUE32
import combo.util.assert
import combo.util.bitSize
import kotlin.math.max

/**
 * Use this to represent an integer in the model. This will use the least amount of bits required, so
 * always specify min/max values to reduce the size of the search space as much as possible.
 * @param min smallest allowed value (inclusive)
 * @param max largest allowed value (inclusive)
 */
class IntVar constructor(name: String, mandatory: Boolean, parent: Value, val min: Int, val max: Int)
    : Variable<Int>(name) {

    init {
        require(max > min) { "Min should be greater than min." }
    }

    override val parentValue: Value = if (mandatory) parent else this

    override val nbrLiterals: Int = let {
        val valueBits = max(Int.bitSize(max), Int.bitSize(min))
        val isSetBit = if (mandatory) 0 else 1
        val signedBit = if (min < 0) 1 else 0
        isSetBit + signedBit + valueBits
    }

    fun value(value: Int): IntLiteral {
        require(value in min..max)
        return IntLiteral(this, value)
    }

    private fun isSigned() = min < 0

    override fun valueOf(instance: Instance, rootIndex: Int): Int? {
        if (!mandatory && !instance[rootIndex]) return null
        val offset = if (mandatory) 0 else 1
        val value = if (isSigned()) instance.getSignedInt(rootIndex + offset, nbrLiterals - offset) else
            instance.getBits(rootIndex + offset, nbrLiterals - offset)
        assert((mandatory && value == 0) || value in min..max)
        return value
    }

    override fun toString() = "IntVar($name in $min:$max)"
    override val defaultEncoder: Encoder<*> get() = IntEncoder

    override fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex) = object : IntMapping {
        override val binaryIx: Int get() = binaryIx
        override val vectorIx: Int get() = vectorIx
        override val binarySize: Int get() = nbrLiterals
        override val vectorSize: Int get() = if (mandatory) 1 else 2
        override val reifiedLiteral: Int get() = if (indicatorVariable) binaryIx.toLiteral(true) else reifiedLiteral(scopedIndex)
        override val indicatorVariable: Boolean get() = !this@IntVar.mandatory
        override val min: Int get() = this@IntVar.min
        override val max: Int get() = this@IntVar.max
        override fun toString() = "IntVarMapping($binaryIx)"
    }
}

class IntLiteral(override val canonicalVariable: IntVar, val value: Int) : Literal {

    override val name: String get() = canonicalVariable.name

    override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
        val ix = index.indexOf(canonicalVariable)
        val offset = if (!canonicalVariable.mandatory) {
            set.add(ix.toLiteral(true))
            1
        } else 0

        var k = value
        for (i in offset until canonicalVariable.nbrLiterals) {
            val kix = ix + i
            set.add(kix.toLiteral(k and 1 == 1))
            k = k ushr 1
        }
    }

    override fun toString() = "IntLiteral($name=$value)"
}

/**
 * Use this to represent a floating point number in the model. This will always use 32-33 bits.
 * Always specify min/max values to reduce the size of the search space as much as possible.
 * @param min smallest allowed value (inclusive)
 * @param max largest allowed value (inclusive)
 */
class FloatVar constructor(name: String, mandatory: Boolean, parent: Value, val min: Float, val max: Float)
    : Variable<Float>(name) {

    init {
        require(max > min) { "Min should be greater than min." }
        require(max <= MAX_VALUE32)
        require(min >= -MAX_VALUE32)
    }

    override val parentValue: Value = if (mandatory) parent else this
    override val nbrLiterals: Int = 32 + if (mandatory) 0 else 1

    fun value(value: Float): FloatLiteral {
        require(value in min..max)
        return FloatLiteral(this, value)
    }

    override fun valueOf(instance: Instance, rootIndex: Int): Float? {
        if (!mandatory && !instance[rootIndex]) return null
        return Float.fromBits(instance.getBits(rootIndex + if (mandatory) 0 else 1, 32))
    }

    override fun toString() = "FloatVar($name in $min:$max)"

    override val defaultEncoder: Encoder<*> get() = FloatEncoder

    override fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex) = object : FloatMapping {
        override val binaryIx: Int get() = binaryIx
        override val vectorIx: Int get() = vectorIx
        override val binarySize: Int get() = nbrLiterals
        override val vectorSize: Int get() = if (mandatory) 1 else 2
        override val reifiedLiteral: Int get() = if (indicatorVariable) binaryIx.toLiteral(true) else reifiedLiteral(scopedIndex)
        override val indicatorVariable: Boolean get() = !this@FloatVar.mandatory
        override val min: Float get() = this@FloatVar.min
        override val max: Float get() = this@FloatVar.max
        override fun toString() = "FloatVarMapping($binaryIx)"
    }
}

class FloatLiteral(override val canonicalVariable: FloatVar, val value: Float) : Literal {

    override val name: String get() = canonicalVariable.name

    override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
        val ix = index.indexOf(canonicalVariable)
        val offset = if (!canonicalVariable.mandatory) {
            set.add(ix.toLiteral(true))
            1
        } else 0

        var k = value.toRawBits()
        for (i in offset until canonicalVariable.nbrLiterals) {
            val kix = ix + i
            set.add(kix.toLiteral(k and 1 == 1))
            k = k ushr 1
        }
    }

    override fun toString() = "FloatLiteral($name=$value)"
}

class BitsVar constructor(
        name: String,
        mandatory: Boolean,
        parent: Value,
        val nbrBits: Int) : Variable<Instance>(name) {

    init {
        require(nbrBits > 0) { "nbrBits must be > 0." }
    }

    override val parentValue: Value = if (mandatory) parent else this
    override val nbrLiterals: Int get() = nbrBits + if (mandatory) 0 else 1

    override fun valueOf(instance: Instance, rootIndex: Int): Instance? {
        if (!mandatory && !instance[rootIndex]) return null
        return BitArray(nbrBits).apply {
            var offset = if (mandatory) 0 else 1
            for (i in field.indices) {
                val nbrBits = if (i == field.lastIndex) nbrBits and 0x1F else 32
                field[i] = instance.getBits(rootIndex + offset, nbrBits)
                offset += nbrBits
            }
        }
    }

    fun value(index: Int) = BitValue(this, index)

    override fun toString(): String {
        return "BitsVar(nbrLiterals=$nbrBits)"
    }

    override val defaultEncoder: Encoder<*> get() = BitsEncoder
    override fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex) = object : VectorMapping {
        override val binaryIx: Int get() = binaryIx
        override val vectorIx: Int get() = vectorIx
        override val binarySize: Int get() = nbrLiterals
        override val reifiedLiteral: Int get() = if (indicatorVariable) binaryIx.toLiteral(true) else reifiedLiteral(scopedIndex)
        override val indicatorVariable: Boolean get() = !mandatory
        override fun toString() = "BitsVarMapping($name)"
    }
}

class BitValue constructor(override val canonicalVariable: BitsVar, val bitIndex: Int) : Value {

    init {
        require(bitIndex in 0 until canonicalVariable.nbrBits) { "BitValue with index=$bitIndex is out of bound with $name." }
    }

    override fun toLiteral(rootIndex: VariableIndex) = (rootIndex.indexOf(canonicalVariable) + bitIndex + if (canonicalVariable.mandatory) 0 else 1).toLiteral(true)

    override fun toString() = "BitValue($name=$bitIndex)"

    override val name: String get() = canonicalVariable.name
}
