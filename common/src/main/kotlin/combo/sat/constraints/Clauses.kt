package combo.sat.constraints

import combo.model.Proposition
import combo.sat.*
import combo.util.*
import kotlin.math.min
import kotlin.random.Random

/*
 * A disjunction is an OR relation between variables:  (a || b || c)
 */
class Disjunction(override val literals: IntCollection) : PropositionalConstraint, Proposition {

    init {
        assert(literals.isNotEmpty())
    }

    override val priority: Int get() = 1000

    override operator fun not() = Conjunction(literals.map { !it })

    override fun violations(instance: Instance, cacheResult: Int) = if (cacheResult > 0 || literals.isEmpty()) 0 else 1

    override fun offset(offset: Int) = Disjunction(literals.map { it.offset(offset) })

    override fun remap(from: Int, to: Int) =
            Disjunction(collectionOf(*literals.mutableCopy().apply {
                val truth = from.toLiteral(true) in literals
                remove(from.toLiteral(truth))
                add(to.toLiteral(truth))
            }.toArray()))

    override fun unitPropagation(unit: Literal): PropositionalConstraint {
        return if (unit in literals) Tautology
        else if (!unit in literals) {
            if (literals.size == 1) {
                Empty
            } else {
                val reducedLiterals = collectionOf(*literals.mutableCopy().apply { remove(!unit) }.toArray())
                val reducedConstraint: PropositionalConstraint =
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
class Conjunction(override val literals: IntCollection) : PropositionalConstraint, Proposition {

    init {
        assert(literals.isNotEmpty())
    }

    override val priority: Int get() = 100

    override operator fun not() = Disjunction(literals.map { !it })

    override fun violations(instance: Instance, cacheResult: Int) = literals.size - cacheResult

    override fun offset(offset: Int) = Conjunction(literals.map { it.offset(offset) })

    override fun remap(from: Int, to: Int) =
            Conjunction(literals.mutableCopy().apply {
                val truth = from.toLiteral(true) in literals
                remove(from.toLiteral(truth))
                add(to.toLiteral(truth))
            })

    override fun unitPropagation(unit: Literal): PropositionalConstraint {
        if (!unit in literals) return Empty
        return if (unit in literals) {
            if (literals.size == 1) Tautology
            else Conjunction(collectionOf(*literals.mutableCopy().apply { remove(unit) }.toArray()))
        } else this
    }

    override fun isUnit() = true

    override fun coerce(instance: MutableInstance, rng: Random) {
        if (literals is IntRangeSet) {
            var ix = min(literals.min.toIx(), literals.max.toIx())
            val ints = (literals.size shr 5) + if (size and 0x1F > 0) 1 else 0
            val value = if (literals.min > 0) -1 else 0
            for (i in 0 until ints) {
                val nbrBits = if (i == ints - 1) ((size - 1) and 0x1F) + 1
                else 32
                if (nbrBits < 32) instance.setBits(ix, nbrBits, value and (-1 shl (size and 0x1F)).inv())
                else instance.setBits(ix, 32, value)
                ix += nbrBits
            }
        } else for (lit in literals) instance.set(lit)
    }

    override fun toString() = literals.joinToString(", ", "Conjunction(", ")") { it.toString() }
}
