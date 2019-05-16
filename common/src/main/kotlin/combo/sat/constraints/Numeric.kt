package combo.sat.constraints

import combo.math.nextFloat
import combo.sat.*
import combo.util.IntRangeSet
import combo.util.assert
import combo.util.bitCount
import kotlin.random.Random

sealed class NumericConstraint(override val literals: IntRangeSet) : Constraint {

    override val priority: Int get() = 200
    override fun cacheUpdate(cacheResult: Int, newLit: Literal) = 0
    override fun cache(instance: Instance) = 0
    override fun isUnit() = false
    override fun unitPropagation(unit: Literal) = this
    override fun remap(from: Int, to: Int) = throw UnsupportedOperationException()
}

class IntBounds(literals: IntRangeSet, val min: Int, val max: Int) : NumericConstraint(literals) {

    constructor(ix: Int, min: Int, max: Int, nbrLiterals: Int) :
            this(IntRangeSet(ix.toLiteral(true), (ix + nbrLiterals - 1).toLiteral(true)), min, max)

    private fun isSigned() = min < 0 || max < 0

    override fun violations(instance: Instance, cacheResult: Int): Int {
        val value = if (isSigned()) instance.getSignedInt(literals.min.toIx(), literals.size)
        else instance.getBits(literals.min.toIx(), literals.size)
        val coercedInt = value.coerceIn(min, max)
        val changedBits = coercedInt xor value
        return Int.bitCount(changedBits)
    }


    override fun offset(offset: Int) = IntBounds(literals.map { it + offset }, min, max)

    override fun coerce(instance: MutableInstance, rng: Random) {
        val coerced = rng.nextInt(min, if (max == Int.MAX_VALUE) max else max + 1)
        if (isSigned()) instance.setSignedInt(literals.min.toIx(), literals.size, coerced)
        else instance.setBits(literals.min.toIx(), literals.size, coerced)
    }

    override fun toString() = "IntBounds(${literals.min.toLiteral(true)} in $min:$max)"
}

class FloatBounds(literals: IntRangeSet, val min: Float, val max: Float) : NumericConstraint(literals) {

    init {
        assert(min.isFinite())
        assert(max.isFinite())
        assert(max > min)
    }

    constructor(ix: Int, min: Float, max: Float) : this(IntRangeSet(ix.toLiteral(true), (ix + 31).toLiteral(true)), min, max)

    private fun Float.coerceIn(minimumValue: Float, maximumValue: Float): Float {
        // We use the compareTo here rather than the '<' and '>' operators to make sure that -0.0f < 0.0f
        if (this.compareTo(minimumValue) < 0) return minimumValue
        if (this.compareTo(maximumValue) > 0) return maximumValue
        return this
    }

    override fun violations(instance: Instance, cacheResult: Int): Int {
        val value = instance.getFloat(literals.min.toIx())
        val coercedBits = value.coerceIn(min, max).toRawBits()
        val valueBits = value.toRawBits()
        val changedBits = coercedBits xor valueBits
        return Int.bitCount(changedBits)
    }

    override fun offset(offset: Int) = FloatBounds(literals.map { it + offset }, min, max)

    override fun coerce(instance: MutableInstance, rng: Random) {
        val coerced = rng.nextFloat(min, max)
        instance.setFloat(literals.min.toIx(), coerced)
    }

    override fun toString() = "FloatBounds(${literals.min.toIx().toLiteral(true)} in $min:$max)"
}