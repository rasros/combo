@file:JvmName("Constraints")

package combo.sat

import combo.sat.Relation.*
import combo.util.IntCollection
import combo.util.IntList
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A constraint must be satisfied during solving. See [Literal] for more information on the binary format of variables.
 */
interface Constraint : Iterable<Literal> {

    val literals: IntCollection
    val size get() = literals.size

    override fun iterator(): IntIterator = literals.iterator()

    fun isUnit(): Boolean = size == 1

    /**
     * Returns the number of changes necessary for the constraint to be satisfied, based on a cached result.
     */
    fun flipsToSatisfy(matches: Int): Int

    /**
     * Update the cached result with the changing literal [lit].
     */
    fun matchesUpdate(lit: Literal, oldMatches: Int) = oldMatches + if (lit in literals) 1 else -1

    /**
     * Calculate the cached result.
     */
    fun matches(l: Instance): Int {
        // Not using Iterable<Int>.sumBy to avoid auto boxing
        var sum = 0
        for (it in literals.iterator()) {
            sum += if (l.literal(it.toIx()) == it) 1 else 0
        }
        return sum
    }

    /**
     * Returns the number of changes necessary for the constraint to be satisfied.
     */
    fun flipsToSatisfy(l: Instance): Int

    /**
     * Returns whether the constraint satisfies the instance.
     */
    fun satisfies(l: Instance) = flipsToSatisfy(l) == 0

    /**
     * Change the constraint based on the value of a unit literal.
     * @throws UnsatisfiableException if there is a contradiction.
     */
    fun propagateUnit(unit: Literal): Constraint

    /**
     * Changes the variable indices used by the literals in the constraint to the new ones given by the mapping in
     * [remappedIxs]. If no changes are necessary then this method can do nothing. This is used as part of the
     * simplification process during initialization.
     */
    fun remap(remappedIxs: IntArray): Constraint {
        val lits = literals.toArray()
        for ((i, l) in lits.withIndex()) {
            val remapped = remappedIxs[lits[i].toIx()].toLiteral(l.toBoolean())
            require(remapped >= 0)
            literals.remove(lits[i])
            literals.add(remapped)
        }
        return this
    }
}

/**
 * A clause is a basic constraint and is one of [Disjunction] or [Conjunction] or [Tautology].
 */
sealed class Clause(override val literals: IntCollection) : Constraint {
    abstract override fun propagateUnit(unit: Literal): Clause
}

/**
 * This constraint is always satisfied.
 */
object Tautology : Clause(IntList(0)) {
    override fun flipsToSatisfy(matches: Int) = 0
    override fun flipsToSatisfy(l: Instance) = 0
    override fun propagateUnit(unit: Literal) = this
    override fun toString() = "Tautology"
}

/*
 * A disjunction is an OR relation between variables:  (a || b || c)
 */
class Disjunction(literals: IntCollection) : Clause(literals) {

    override fun flipsToSatisfy(matches: Int) = if (matches > 0 || literals.isEmpty()) 0 else 1

    override fun flipsToSatisfy(l: Instance): Int {
        for (lit in literals) {
            val ix = lit.toIx()
            if (l[ix] == lit.toBoolean())
                return 0
        }
        return 1
    }

    override fun propagateUnit(unit: Literal): Clause {
        return if (unit in literals) Tautology
        else if (!unit in literals) {
            if (literals.size == 1) throw UnsatisfiableException(literal = unit)
            else {
                literals.copy().let {
                    it.remove(!unit)
                    if (it.size == 1) Conjunction(it)
                    else Disjunction(it)
                }
            }
        } else return this
    }

    override fun toString() = literals.joinToString(", ", "Disjunction(", ")") { it.toString() }
}

/**
 * A conjunction is an AND relation between variables: a && b && c
 */
class Conjunction(literals: IntCollection) : Clause(literals) {

    override fun flipsToSatisfy(matches: Int) = literals.size - matches
    override fun flipsToSatisfy(l: Instance): Int {
        var unmatched = 0
        for (lit in literals)
            if (l.literal(lit.toIx()) != lit) unmatched++
        return unmatched
    }

    override fun propagateUnit(unit: Literal): Clause {
        if (!unit in literals) throw UnsatisfiableException(literal = unit)
        return this
    }

    override fun isUnit() = true
    override fun toString() = literals.joinToString(", ", "Conjunction(", ")") { it.toString() }
}

/**
 * Reified encodes the constraint [literal] <=> [clause]. That is, the constraint is satisfied when both the
 * [clause] and [literal] is satisfied or when neither of them are.
 */
class Reified(val literal: Literal, val clause: Clause) : Constraint {

    init {
        require(clause.literals.isNotEmpty()) { "Literals in clause should not be empty." }
        if (literal in clause.literals || !literal in clause.literals)
            throw IllegalArgumentException("Literal appears in clause for reified.")
    }

    override val literals = clause.literals.copy().apply {
        add(literal)
    }

    override fun matches(l: Instance): Int {
        val clauseMatches = clause.matches(l)
        return clauseMatches.toLiteral(l.literal(literal.toIx()) == literal)
    }

    override fun matchesUpdate(lit: Literal, oldMatches: Int): Int {
        val oldClauseMatch = oldMatches.toIx()
        return if (literal.toIx() == lit.toIx()) {
            oldClauseMatch.toLiteral(lit == literal)
        } else {
            val oldLiteralMatch = oldMatches.toBoolean()
            clause.matchesUpdate(lit, oldClauseMatch).toLiteral(oldLiteralMatch)
        }
    }

    override fun flipsToSatisfy(matches: Int): Int {
        val clauseFlips = clause.flipsToSatisfy(matches.toIx())
        return if (matches.toBoolean()) min(1, clauseFlips)
        else return if (clauseFlips == 0) 1 else 0
    }

    override fun flipsToSatisfy(l: Instance): Int {
        return if (l[literal.toIx()] == literal.toBoolean()) {
            min(1, clause.flipsToSatisfy(l))
        } else {
            // Make sure the clause CAN be negated
            when (clause) {
                is Disjunction -> {
                    if (clause.literals.any { l[it.toIx()] == it.toBoolean() }) 1
                    else 0
                }
                is Conjunction -> {
                    if (clause.literals.all { l[it.toIx()] == it.toBoolean() }) 1
                    else 0
                }
                is Tautology -> 1
            }
        }
    }

    override fun propagateUnit(unit: Literal): Constraint {
        return when {
            unit == literal -> clause
            unit.toIx() == literal.toIx() -> {
                val negated = clause.literals.map { !it }
                when (clause) {
                    is Disjunction -> Conjunction(negated)
                    is Conjunction -> Disjunction(negated)
                    is Tautology -> throw UnsatisfiableException("The model is unsatisfiable by unit propagation, " +
                            "there is a contradiction in the specification.", literal = unit)
                }
            }
            clause is Disjunction -> {
                val propagatedClause = clause.propagateUnit(unit)
                when (propagatedClause) {
                    clause -> this
                    is Tautology -> Conjunction(IntList(intArrayOf(literal)))
                    else -> Reified(literal, propagatedClause)
                }
            }
            clause is Conjunction -> {
                if (clause.literals.any { !it == unit }) Conjunction(IntList(intArrayOf(!literal)))
                else if (unit in clause.literals) {
                    if (clause.literals.size == 1) Conjunction(IntList(intArrayOf(literal)))
                    else Reified(literal, Conjunction(clause.literals.copy().apply { remove(unit) }))
                } else this
            }
            else -> this
        }
    }

    fun toCnf(): Sequence<Disjunction> {
        return when (clause) {
            is Disjunction -> {
                val c1 = clause.literals.asSequence().map { Disjunction(IntList(intArrayOf(literal, !it))) }
                val c2 = sequenceOf(Disjunction(clause.literals.copy().apply { add(!literal) }))
                c1 + c2
            }
            is Conjunction -> {
                val c1 = clause.literals.asSequence().map { Disjunction(IntList(intArrayOf(!literal, it))) }
                val c2 = sequenceOf(Disjunction((clause.literals.map { !it }.apply { add(literal) })))
                c1 + c2
            }
            is Tautology -> sequenceOf(Disjunction(IntList(intArrayOf(literal))))
        }
    }

    override fun remap(remappedIxs: IntArray): Constraint {
        val reifiedLiteral = remappedIxs[literal.toIx()].toLiteral(literal.toBoolean())
        return Reified(reifiedLiteral, clause.remap(remappedIxs) as Clause)
    }

    override fun toString() = "Reified($literal, $clause)"
}

/**
 * Cardinality constraint encodes a linear equality [relation] with a [degree].
 * Note that all literals must be true, because they are used as linear constraints by some solvers
 * (e.g. x+y+z < degree). These solvers cannot handle [Relation.NE] because it cannot be expressed as a linear relation.
 */
class Cardinality(override val literals: IntCollection, val degree: Int, val relation: Relation) : Constraint {

    init {
        for (l in literals) require(l.toBoolean()) {
            "Can only have non-negated literals in $this. Offending literal: $l"
        }
        require(degree >= 0) { "Degree must be >= 0 in $this." }
        require(literals.isNotEmpty()) { "Literals should not be empty." }
        if (!isSatisfiable(degree, literals.size, relation))
            throw UnsatisfiableException("$this is not satisfiable, (${literals.size} cannot be ${relation.operator}).")
    }

    private companion object {
        fun isSatisfiable(degree: Int, nbrLiterals: Int, relation: Relation): Boolean {
            return when (relation) {
                EQ -> degree in 0..nbrLiterals
                NE -> true
                else -> relation.flipsToSatisfy(nbrLiterals, degree) == 0 || relation.flipsToSatisfy(0, degree) == 0
            }
        }
    }

    override fun flipsToSatisfy(matches: Int) = relation.flipsToSatisfy(matches, degree)

    override fun flipsToSatisfy(l: Instance): Int {
        var matches = 0
        for (lit in literals) {
            if (l.literal(lit.toIx()) == lit) matches++
        }
        return relation.flipsToSatisfy(matches, degree)
    }

    override fun propagateUnit(unit: Literal): Constraint {
        return if (unit.toIx().toLiteral(true) in literals) {
            val copy = literals.copy().apply { if (unit.toBoolean()) remove(unit) else remove(!unit) }
            val d = degree - if (unit.toBoolean()) 1 else 0
            if (!isSatisfiable(d, copy.size, relation))
                throw UnsatisfiableException(
                        "$this with unit $unit is not satisfiable (${copy.size} cannot be ${relation.operator} $d).")
            else if (d >= copy.size && relation === LE) Tautology
            else if (d > copy.size && relation === LT) Tautology
            else if (d <= 0 && relation === GE) Tautology
            else if (d < 0 && relation === GT) Tautology
            else if ((d < 0 || d > copy.size) && relation === NE) Tautology
            else if (copy.isEmpty()) {
                if (relation.flipsToSatisfy(0, d) == 0) Tautology
                else throw UnsatisfiableException(
                        "$this with unit $unit is not satisfiable (${copy.size} cannot be ${relation.operator} $d).")
            } else Cardinality(copy, d, relation)
        } else this
    }

    override fun isUnit(): Boolean {
        // TODO could be more cases where literals.size > 1
        return if (literals.size == 1) {
            when (relation) {
                GT -> degree == 0
                GE -> degree == 1
                LE -> false
                LT -> false
                NE -> degree == 0
                EQ -> degree == 1
            }
        } else false
    }

    override fun toString() = literals.joinToString(", ", "Cardinality(", ") $relation $degree") { it.toString() }
}

enum class Relation(val operator: String) {
    GT(">") {
        override fun flipsToSatisfy(matches: Int, degree: Int) = max(0, 1 + degree - matches)
    },
    GE(">=") {
        override fun flipsToSatisfy(matches: Int, degree: Int) = max(0, degree - matches)
    },
    LE("<=") {
        override fun flipsToSatisfy(matches: Int, degree: Int) = max(0, matches - degree)
    },
    LT("<") {
        override fun flipsToSatisfy(matches: Int, degree: Int) = max(0, 1 + matches - degree)
    },
    NE("!=") {
        override fun flipsToSatisfy(matches: Int, degree: Int) = if (matches == degree) 1 else 0
    },
    EQ("=") {
        override fun flipsToSatisfy(matches: Int, degree: Int) = abs(matches - degree)
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

    abstract fun flipsToSatisfy(matches: Int, degree: Int): Int
}
