package combo.model

import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.sat.constraints.FloatBounds
import combo.sat.constraints.IntBounds
import combo.sat.constraints.ReifiedImplies
import combo.util.*
import kotlin.math.max

/**
 * Use this to represent an integer in the model. This will use the least amount of bits required, so
 * always specify min/max values to reduce the size of the search space as much as possible.
 * @param min smallest allowed value (inclusive)
 * @param max largest allowed value (inclusive)
 */
class IntVar constructor(name: String, parent: Value?, val min: Int, val max: Int)
    : Variable<Int, Int>(name) {

    init {
        require(max > min) { "Min should be greater than min." }
    }

    override val reifiedValue = parent ?: this

    override val nbrLiterals: Int = let {
        val valueBits = max(Int.bitSize(max), Int.bitSize(min))
        val isSetBit = if (mandatory) 0 else 1
        val signedBit = if (min < 0) 1 else 0
        isSetBit + signedBit + valueBits
    }

    override fun value(value: Int): IntLiteral {
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

    override fun implicitConstraints(scope: Scope, index: VariableIndex): Sequence<Constraint> {
        val ix = index.indexOf(this)
        val offset = if (mandatory) 0 else 1
        val zeros = IntRangeCollection((ix + nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
        return if (reifiedValue is Root) sequenceOf(IntBounds(ix + offset, min, max, nbrLiterals - offset))
        else sequenceOf(
                ReifiedImplies(reifiedValue.not().toLiteral(index), Conjunction(zeros)),
                ReifiedImplies(reifiedValue.toLiteral(index), IntBounds(ix + offset, min, max, nbrLiterals - offset)))
    }

    override fun toString() = "IntVar($name in $min:$max)"

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
class FloatVar constructor(name: String, parent: Value?, val min: Float, val max: Float)
    : Variable<Float, Float>(name) {

    init {
        require(max > min) { "Min should be greater than min." }
        require(max <= MAX_VALUE32)
        require(min >= -MAX_VALUE32)
    }

    override val reifiedValue = parent ?: this
    override val nbrLiterals: Int = 32 + if (mandatory) 0 else 1

    override fun value(value: Float): FloatLiteral {
        require(value in min..max)
        return FloatLiteral(this, value)
    }

    override fun valueOf(instance: Instance, rootIndex: Int): Float? {
        if (!mandatory && !instance[rootIndex]) return null
        return Float.fromBits(instance.getBits(rootIndex + if (mandatory) 0 else 1, 32))
    }

    override fun implicitConstraints(scope: Scope, index: VariableIndex): Sequence<Constraint> {
        val ix = index.indexOf(this)
        val offset = if (mandatory) 0 else 1
        val zeros = IntRangeCollection((ix + nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
        return if (reifiedValue is Root) sequenceOf(FloatBounds(ix + offset, min, max))
        else sequenceOf(
                ReifiedImplies(reifiedValue.not().toLiteral(index), Conjunction(zeros)),
                ReifiedImplies(reifiedValue.toLiteral(index), FloatBounds(ix + offset, min, max)))
    }

    override fun toString() = "FloatVar($name in $min:$max)"

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
        parent: Value?,
        val nbrBits: Int) : Variable<Int, Instance>(name) {

    init {
        require(nbrBits > 0) { "nbrBits must be > 0." }
    }

    override val reifiedValue = parent ?: this
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

    /**
     * @param value is index of the bit field.
     */
    override fun value(value: Int) = BitValue(this, value)

    override fun implicitConstraints(scope: Scope, index: VariableIndex): Sequence<Constraint> {
        if (reifiedValue is Root) return emptySequence()
        val ix = index.indexOf(this)
        val offset = if (mandatory) 0 else 1
        val zeros = IntRangeCollection((ix + nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
        return sequenceOf(ReifiedImplies(reifiedValue.not().toLiteral(index), Conjunction(zeros)))
    }

    override fun toString(): String {
        return "BitsVar(nbrLiterals=$nbrBits)"
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
