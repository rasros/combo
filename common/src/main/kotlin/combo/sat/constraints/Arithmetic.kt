package combo.sat.constraints

import combo.model.IntVar
import combo.sat.*
import combo.sat.constraints.Relation.*
import combo.util.*
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Linear constraint encodes a linear sum [relation] with a [degree], e.g. 2*x2 - 3*x4 <= 2.
 * Some solvers cannot handle [Relation.NE] because it cannot be expressed as a linear relation.
 * Intermediate results are stored in a 32 bit integer so there is a real risk of integer overflow for large weights.
 */
class Linear(override val literals: IntHashMap, val weights: IntArray, val degree: Int, val relation: Relation) : PropositionalConstraint {

    override val priority: Int get() = 500 - literals.size

    val lowerBound = weights.sumBy { min(0, it) }
    val upperBound = weights.sumBy { max(0, it) }
    private val average = max(1, weights.sumBy { it.absoluteValue } / weights.size)

    init {
        assert(weights.size == literals.size)
        assert(literals.isNotEmpty())
    }

    override operator fun not() = Linear(literals, weights, degree, !relation)

    override fun cacheUpdate(cacheResult: Int, newLit: Int) = cacheResult +
            if (newLit in literals) weights[literals[newLit]] else -weights[literals[!newLit]]

    override fun cache(instance: Instance): Int {
        var sum = 0
        for (entry in literals.entryIterator()) {
            val lit = entry.key()
            val index = entry.value()
            if (instance.literal(lit.toIx()) == lit) sum += weights[index]
        }
        return sum
    }

    override fun violations(instance: Instance, cacheResult: Int): Int {
        val absoluteViolations = relation.violations(cacheResult, degree)
        val approximateViolations = absoluteViolations / average
        return if (approximateViolations == 0 && absoluteViolations != 0) 1
        else approximateViolations.coerceAtMost(literals.size)
    }

    override fun unitPropagation(unit: Int): PropositionalConstraint {
        val ix1 = literals[unit, -1]
        val ix2 = literals[!unit, -1]
        return if (ix1 >= 0 || ix2 >= 0) {
            val litCopy = literals.copy().apply { if (ix1 >= 0) remove(unit) else remove(!unit) }
            val weightIx = if (ix1 >= 0) literals[unit] else literals[!unit]
            val d = degree - if (ix1 >= 0) weights[weightIx] else 0

            val adjustedLower = lowerBound - min(0, weights[weightIx])
            val adjustedUpper = upperBound - max(0, weights[weightIx])

            return if (litCopy.size == 0) {
                assert(adjustedLower == 0)
                assert(adjustedUpper == 0)
                if (relation.violations(0, d) == 0) Tautology
                else Empty
            } else if (relation.isEmpty(adjustedLower, adjustedUpper, d)) Empty
            else if (relation.isTautology(adjustedLower, adjustedUpper, d)) Tautology
            else {
                val weightCopy = weights.removeAt(weightIx)
                for (lit in litCopy) {
                    if (litCopy[lit] > weightIx)
                    // This does not change hash position so is safe from concurrent modification
                        litCopy[lit] = litCopy[lit] - 1
                }
                return Linear(litCopy, weightCopy, d, relation)
            }
        } else this
    }

    /**
     * Unit literals for Arithmetic constraint is tricky because there can be free literals in a constraint containing
     * unit literals. For example if some weights are 0. Or -1x1 + 10x2 > 8, here x2=1 and x1 can be 0 or 1.
     * For this reason we skip unit literals for Arithmetic constraint.
     */
    override fun unitLiterals() = throw UnsupportedOperationException()

    override fun isUnit() = false

    override fun coerce(instance: Instance, rng: Random) {
        val card = this
        var value = cache(instance)
        val perm = card.literals.permutation(rng)
        if (card.relation == LE || card.relation == LT || card.relation == EQ) {
            val d = if (card.relation == LT) card.degree - 1 else card.degree
            while (value > d && perm.hasNext()) {
                val lit = perm.nextInt()
                if (instance.literal(lit.toIx()) == lit) {
                    instance.flip(lit.toIx())
                    value--
                }
            }
        }
        if (card.relation == GE || card.relation == GT || card.relation == EQ) {
            val d = if (card.relation == GT) card.degree + 1 else card.degree
            while (value < d && perm.hasNext()) {
                val lit = perm.nextInt()
                if (instance.literal(lit.toIx()) != lit) {
                    instance.flip(lit.toIx())
                    value++
                }
            }
        }
        if (card.relation == NE && value == card.degree && perm.hasNext()) {
            instance.flip(perm.nextInt().toIx())
        }
    }

    override fun toString() = literals.joinToString(", ", "Linear(", " ${relation.operator} $degree)") {
        "${weights[literals[it]]}x$it"
    }
}

/**
 * Cardinality constraint encodes a linear equality [relation] with a [degree].
 * Some solvers cannot handle [Relation.NE] because it cannot be expressed as a linear relation.
 */
class Cardinality(override val literals: IntCollection, val degree: Int, val relation: Relation) : PropositionalConstraint {

    override val priority: Int get() = 400 - literals.size

    init {
        assert(degree >= 0)
        assert(literals.isNotEmpty())
    }

    override operator fun not() = Cardinality(literals, degree, !relation)

    override fun violations(instance: Instance, cacheResult: Int) = relation.violations(cacheResult, degree).coerceAtMost(literals.size)

    override fun unitPropagation(unit: Int): PropositionalConstraint {
        val match = unit in literals
        return if (match || !unit in literals) {

            val copy = literals.mutableCopy(nullValue = 0).apply { if (match) remove(unit) else remove(!unit) }
            val d = degree - if (match) 1 else 0

            return if (relation.isEmpty(0, copy.size, d)) Empty
            else if (relation.isTautology(0, copy.size, d)) Tautology
            else {
                val card = Cardinality(copy, d, relation)
                if (card.isUnit()) Conjunction(collectionOf(*card.unitLiterals()))
                else card
            }
        } else this
    }

    override fun isUnit() = relation.isUnit(0, literals.size, degree)

    override fun unitLiterals(): Literals {
        assert(isUnit())
        return when (relation) {
            GT -> literals.toArray()
            GE -> literals.toArray()
            LE -> literals.toArray().mapArray { !it }
            LT -> literals.toArray().mapArray { !it }
            NE -> throw IllegalArgumentException()
            EQ -> if (degree == 0) literals.toArray().mapArray { !it } else literals.toArray()
        }
    }

    override fun coerce(instance: Instance, rng: Random) {
        val card = this
        var value = cache(instance)
        val perm = card.literals.permutation(rng)
        if (card.relation == LE || card.relation == LT || card.relation == EQ) {
            val d = if (card.relation == LT) card.degree - 1 else card.degree
            while (value > d && perm.hasNext()) {
                val lit = perm.nextInt()
                if (instance.literal(lit.toIx()) == lit) {
                    instance.flip(lit.toIx())
                    value--
                }
            }
        }
        if (card.relation == GE || card.relation == GT || card.relation == EQ) {
            val d = if (card.relation == GT) card.degree + 1 else card.degree
            while (value < d && perm.hasNext()) {
                val lit = perm.nextInt()
                if (instance.literal(lit.toIx()) != lit) {
                    instance.flip(lit.toIx())
                    value++
                }
            }
        }
        if (card.relation == NE && value == card.degree && perm.hasNext()) {
            instance.flip(perm.nextInt().toIx())
        }
    }

    override fun toString() = literals.joinToString(", ", "Cardinality(", " ${relation.operator} $degree)") { it.toString() }
}

class CardinalityVar(valueLiterals: IntCollection, val degreeVar: IntVar, val varIndex: Int, val parentLiteral: Int, val relation: Relation) : PropositionalConstraint {

    override val priority: Int get() = 400 - literals.size
    override val literals: IntUnionCollection

    init {
        assert(valueLiterals.isNotEmpty())
        val l = varIndex.toLiteral(true)
        val degreeLiterals = IntRangeCollection(l, l + degreeVar.nbrValues)
        literals = if (parentLiteral == 0) IntUnionCollection(valueLiterals, degreeLiterals)
        else IntUnionCollection(valueLiterals, IntUnionCollection(degreeLiterals, collectionOf(parentLiteral)))
    }

    override fun cacheUpdate(cacheResult: Int, newLit: Int) = cacheResult + if (newLit in literals.a) 1 else -1

    override fun cache(instance: Instance): Int {
        var sum = 0
        if (literals.a is IntRangeCollection) {
            val lits = literals.a
            var ix = min(lits.min.toIx(), lits.max.toIx())
            val ints = (size shr 5) + if (size and 0x1F > 0) 1 else 0
            for (i in 0 until ints) {
                val nbrBits = if (i == ints - 1) ((size - 1) and 0x1F) + 1 else 32
                val value = instance.getBits(ix, nbrBits)
                sum += if (lits.min < 0) nbrBits - Int.bitCount(value) else Int.bitCount(value)
                ix += nbrBits
            }
            return sum
        }
        for (lit in literals.a.iterator()) {
            if (instance.literal(lit.toIx()) == lit) sum++
        }
        return sum
    }

    override operator fun not() = CardinalityVar(literals.a, degreeVar, varIndex, parentLiteral, !relation)

    // TODO unitPropagation by having a units as field

    override fun violations(instance: Instance, cacheResult: Int): Int {
        val degree = degreeVar.valueOf(instance, varIndex, parentLiteral) ?: return 0
        return relation.violations(cacheResult, degree).coerceAtMost(literals.a.size)
    }

    override fun coerce(instance: Instance, rng: Random) {
        val degree = degreeVar.valueOf(instance, varIndex, parentLiteral) ?: return
        Cardinality(literals.a, degree, relation).coerce(instance, rng)
    }

    override fun toString() = literals.a.joinToString(", ", "CardinalityVar(", " ${relation.operator} $varIndex)") { it.toString() }
}

enum class Relation(val operator: String) {
    GT(">") {
        override fun violations(value: Int, degree: Int) = max(0, 1 + degree - value)
        override fun isTautology(lowerBound: Int, upperBound: Int, degree: Int) = degree < lowerBound
        override fun isUnit(lowerBound: Int, upperBound: Int, degree: Int) = degree == upperBound - 1
    },
    GE(">=") {
        override fun violations(value: Int, degree: Int) = max(0, degree - value)
        override fun isTautology(lowerBound: Int, upperBound: Int, degree: Int) = degree <= lowerBound
        override fun isUnit(lowerBound: Int, upperBound: Int, degree: Int) = degree == upperBound
    },
    LE("<=") {
        override fun violations(value: Int, degree: Int) = max(0, value - degree)
        override fun isTautology(lowerBound: Int, upperBound: Int, degree: Int) = degree >= upperBound
        override fun isUnit(lowerBound: Int, upperBound: Int, degree: Int) = degree == lowerBound
    },
    LT("<") {
        override fun violations(value: Int, degree: Int) = max(0, 1 + value - degree)
        override fun isTautology(lowerBound: Int, upperBound: Int, degree: Int) = degree > upperBound
        override fun isUnit(lowerBound: Int, upperBound: Int, degree: Int) = degree == lowerBound + 1
    },
    NE("!=") {
        override fun violations(value: Int, degree: Int) = if (value == degree) 1 else 0
        override fun isEmpty(lowerBound: Int, upperBound: Int, degree: Int) = (lowerBound == upperBound && lowerBound == degree)
        override fun isTautology(lowerBound: Int, upperBound: Int, degree: Int) = degree !in lowerBound..upperBound
        override fun isUnit(lowerBound: Int, upperBound: Int, degree: Int) = false
    },
    EQ("=") {
        override fun violations(value: Int, degree: Int) = abs(value - degree)
        override fun isEmpty(lowerBound: Int, upperBound: Int, degree: Int) = degree !in lowerBound..upperBound
        override fun isTautology(lowerBound: Int, upperBound: Int, degree: Int) = (lowerBound == upperBound && lowerBound == degree)
        override fun isUnit(lowerBound: Int, upperBound: Int, degree: Int) = degree == lowerBound || degree == upperBound
    };

    operator fun not(): Relation {
        return when (this) {
            GT -> LE
            GE -> LT
            LE -> GT
            LT -> GE
            NE -> EQ
            EQ -> NE
        }
    }

    abstract fun violations(value: Int, degree: Int): Int
    open fun isEmpty(lowerBound: Int, upperBound: Int, degree: Int) = violations(upperBound, degree) != 0 && violations(lowerBound, degree) != 0
    abstract fun isTautology(lowerBound: Int, upperBound: Int, degree: Int): Boolean
    abstract fun isUnit(lowerBound: Int, upperBound: Int, degree: Int): Boolean
}