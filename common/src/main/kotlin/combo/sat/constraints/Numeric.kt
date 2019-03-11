package combo.sat.constraints

import combo.math.nextFloat
import combo.sat.*
import combo.util.IntRangeSet
import combo.util.bitCount
import kotlin.random.Random

sealed class NumericConstraint(override val literals: IntRangeSet) : Constraint {

    override val priority: Int = 200
    override fun cacheUpdate(instance: Instance, cacheResult: Int, newLit: Literal) = 0
    override fun cache(instance: Instance) = 0
    override fun isUnit() = false
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

    override fun unitPropagation(unit: Literal): Constraint {
        val isMin = unit.toIx() == literals.min.toIx()
        val isMax = unit.toIx() == literals.max.toIx()
        return if (isMin || isMax) {
            val mask = 1 shl unit.toIx() - literals.min.toIx()
            val newMin = if (unit.toBoolean()) this.min or mask else this.min and mask.inv()
            val newMax = if (unit.toBoolean()) this.max or mask else this.max and mask.inv()
            if (newMin > newMax) return Empty
            if (literals.size == 1) return Tautology
            val newIx = literals.min.toIx() + if (isMin) 1 else 0
            IntBounds(newIx, newMin, newMax, literals.size - 1)
        } else this
    }

    override fun offset(offset: Int) = IntBounds(literals.map { it + offset }, min, max)

    override fun coerce(instance: MutableInstance, rng: Random) {
        val coerced = rng.nextInt(min, max + 1)
        if (isSigned()) instance.setSignedInt(literals.min.toIx(), literals.size, coerced)
        else instance.setBits(literals.min.toIx(), literals.size, coerced)
    }

    override fun toString() = "IntBounds(${literals.min.toLiteral(true)} in $min:$max)"
}

class FloatBounds(literals: IntRangeSet, val min: Float, val max: Float) : NumericConstraint(literals) {

    constructor(ix: Int, min: Float, max: Float) : this(IntRangeSet(ix.toLiteral(true), (ix + 31).toLiteral(true)), min, max)

    override fun violations(instance: Instance, cacheResult: Int): Int {
        val value = instance.getFloat(literals.min.toIx())
        val coercedBits = value.coerceIn(min, max).toRawBits()
        val valueBits = value.toRawBits()
        val changedBits = coercedBits xor valueBits
        return Int.bitCount(changedBits)
    }

    override fun unitPropagation(unit: Literal): Constraint {
        return this
    }

    override fun offset(offset: Int) = FloatBounds(literals.map { it + offset }, min, max)

    override fun coerce(instance: MutableInstance, rng: Random) {
        val coerced = rng.nextFloat(min, max)
        instance.setFloat(literals.min.toIx(), coerced)
    }

    override fun toString() = "FloatBounds(${literals.min.toIx().toLiteral(true)} in $min:$max)"
}