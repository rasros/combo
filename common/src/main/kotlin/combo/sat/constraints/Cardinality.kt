package combo.sat.constraints

import combo.sat.*
import combo.sat.constraints.Relation.*
import combo.util.*
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Cardinality constraint encodes a linear equality [relation] with a [degree].
 * Note that all literals must be true, because they are used as linear constraints by some solvers
 * (e.g. x+y+z < degree). These solvers cannot handle [Relation.NE] because it cannot be expressed as a linear relation.
 * TODO allow negated literals
 */
class Cardinality(override val literals: IntCollection, val degree: Int, val relation: Relation) : NegatableConstraint {
    override val priority: Int = 500

    init {
        for (l in literals) assert(l.toBoolean()) {
            "Can only have non-negated literals in $this. Offending literal: $l"
        }
        assert(degree >= 0) { "Degree must be > 0 in $this." }
        assert(literals.isNotEmpty()) { "Literals should not be empty." }
        assert(isSatisfiable(degree, literals.size, relation))
    }

    override operator fun not() = Cardinality(literals, degree, relation.not())

    private companion object {
        fun isSatisfiable(degree: Int, nbrLiterals: Int, relation: Relation): Boolean {
            return when (relation) {
                EQ -> degree in 0..nbrLiterals
                NE -> true
                else -> relation.violations(nbrLiterals, degree) == 0 || relation.violations(0, degree) == 0
            }
        }
    }

    override fun violations(instance: Instance, cacheResult: Int) = relation.violations(cacheResult, degree)

    override fun offset(offset: Int) = Cardinality(literals.map { it.offset(offset) }, degree, relation)

    override fun unitPropagation(unit: Literal): NegatableConstraint {
        return if (unit.toIx().toLiteral(true) in literals) {
            val copy = literals.mutableCopy().apply { if (unit.toBoolean()) remove(unit) else remove(!unit) }
            val d = degree - if (unit.toBoolean()) 1 else 0
            if (!isSatisfiable(d, copy.size, relation))
                return Empty
            else if (d >= copy.size && relation === LE) Tautology
            else if (d > copy.size && relation === LT) Tautology
            else if (d <= 0 && relation === GE) Tautology
            else if (d < 0 && relation === GT) Tautology
            else if ((d < 0 || d > copy.size) && relation === NE) Tautology
            else if (copy.isEmpty()) {
                val emptyConstraint: NegatableConstraint = if (relation.violations(0, d) == 0) Tautology
                else Empty
                emptyConstraint
            } else {
                val card = Cardinality(copy, d, relation)
                if (card.isUnit()) Conjunction(collectionOf(*card.unitLiterals()))
                else card
            }
        } else this
    }

    override fun isUnit(): Boolean {
        return when (relation) {
            GT -> degree == literals.size - 1
            GE -> degree == literals.size
            LE -> degree == 0
            LT -> degree == 1
            NE -> literals.size == 1
            EQ -> degree == 0 || degree == literals.size
        }
    }

    override fun unitLiterals(): Literals {
        return when (relation) {
            GT -> literals.toArray()
            GE -> literals.toArray()
            LE -> literals.toArray().mapArray { !it }
            LT -> literals.toArray().mapArray { !it }
            NE -> if (degree == 0) literals.toArray() else literals.toArray().mapArray { !it }
            EQ -> if (degree == 0) literals.toArray().mapArray { !it } else literals.toArray()
        }
    }

    override fun coerce(instance: MutableInstance, rng: Random) {
        val card = this
        var matches = cache(instance)
        val perm = card.literals.permutation(rng)
        if (card.relation == LE || card.relation == LT || card.relation == EQ) {
            val d = if (card.relation == LT) card.degree - 1 else card.degree
            while (matches > d) {
                val lit = perm.nextInt()
                if (instance[lit.toIx()]) {
                    instance.flip(lit.toIx())
                    matches--
                }
            }
        }
        if (card.relation == GE || card.relation == GT || card.relation == EQ) {
            val d = if (card.relation == GT) card.degree + 1 else card.degree
            while (matches < d) {
                val lit = perm.nextInt()
                if (!instance[lit.toIx()]) {
                    instance.flip(lit.toIx())
                    matches++
                }
            }
        }
        if (card.relation == NE && matches == card.degree && perm.hasNext()) {
            instance.flip(perm.nextInt().toIx())
        }
    }

    override fun toString() = literals.joinToString(", ", "Cardinality(", " ${relation.operator} $degree)") { it.toString() }
}

enum class Relation(val operator: String) {
    GT(">") {
        override fun violations(matches: Int, degree: Int) = max(0, 1 + degree - matches)
    },
    GE(">=") {
        override fun violations(matches: Int, degree: Int) = max(0, degree - matches)
    },
    LE("<=") {
        override fun violations(matches: Int, degree: Int) = max(0, matches - degree)
    },
    LT("<") {
        override fun violations(matches: Int, degree: Int) = max(0, 1 + matches - degree)
    },
    NE("!=") {
        override fun violations(matches: Int, degree: Int) = if (matches == degree) 1 else 0
    },
    EQ("=") {
        override fun violations(matches: Int, degree: Int) = abs(matches - degree)
    };

    fun not(): Relation {
        return when (this) {
            GT -> LE
            GE -> LT
            LE -> GT
            LT -> GE
            NE -> EQ
            EQ -> NE
        }
    }

    companion object {
        @JvmStatic
        fun String.toRelation(): Relation {
            for (r in values())
                if (r.operator == this) return r
            if (this == "==") return EQ
            throw IllegalArgumentException("Unknown relation: $this.")
        }
    }

    abstract fun violations(matches: Int, degree: Int): Int
}