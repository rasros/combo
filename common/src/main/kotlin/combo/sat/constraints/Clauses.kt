package combo.sat.constraints

import combo.model.BasicExpression
import combo.sat.*
import combo.util.*
import kotlin.math.sign
import kotlin.random.Random

/*
 * A disjunction is an OR relation between variables:  (a || b || c)
 */
class Disjunction(override val literals: IntCollection) : NegatableConstraint, BasicExpression {

    override val priority: Int = 1000

    override operator fun not() = Conjunction(literals.map { !it })

    override fun violations(instance: Instance, cacheResult: Int) = if (cacheResult > 0 || literals.isEmpty()) 0 else 1

    override fun offset(offset: Int) = Disjunction(literals.map { it.offset(offset) })

    override fun unitPropagation(unit: Literal): NegatableConstraint {
        return if (unit in literals) Tautology
        else if (!unit in literals) {
            if (literals.size == 1) {
                Empty
            } else {
                val reducedLiterals = collectionOf(*literals.mutableCopy().apply { remove(!unit) }.toArray())
                val reducedConstraint: NegatableConstraint =
                        if (reducedLiterals.size == 1) Conjunction(reducedLiterals)
                        else Disjunction(reducedLiterals)
                reducedConstraint
            }
        } else this
    }

    override fun coerce(instance: MutableInstance, rng: Random) {
        if (!satisfies(instance))
            instance.set(literals.random(rng))
    }

    override fun toString() = literals.joinToString(", ", "Disjunction(", ")") { it.toString() }
}

/**
 * A conjunction is an AND relation between variables: a && b && c
 */
class Conjunction(override val literals: IntCollection) : NegatableConstraint, BasicExpression {

    override val priority: Int = 100

    override operator fun not() = Disjunction(literals.map { !it })

    override fun violations(instance: Instance, cacheResult: Int) = literals.size - cacheResult

    override fun offset(offset: Int) = Conjunction(literals.map { it.offset(offset) })

    override fun unitPropagation(unit: Literal): NegatableConstraint {
        if (!unit in literals) return Empty
        return if (unit in literals) {
            if (literals.size == 1) Tautology
            else Conjunction(collectionOf(*literals.mutableCopy().apply { remove(unit) }.toArray()))
        } else this
    }

    override fun isUnit() = true

    override fun coerce(instance: MutableInstance, rng: Random) {
        if (literals is IntRangeSet && (literals.min.sign == literals.max.sign)) {
            val ix = literals.min.toIx()
            val ints = (literals.size shr 5) + if (size and 0x1F > 0) 1 else 0
            val value = if (literals.min > 0) -1 else 0
            for (i in 0 until ints) {
                if (i == ints - 1) instance.setBits(ix, size and 0x1F, value and (-1 shl (size and 0x1F)).inv())
                else instance.setBits(ix, 32, value)
            }
        } else for (lit in this) instance.set(lit)
    }

    override fun toString() = literals.joinToString(", ", "Conjunction(", ")") { it.toString() }
}
