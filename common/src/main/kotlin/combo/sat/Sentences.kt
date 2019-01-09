@file:JvmName("Sentences")

package combo.sat

import combo.util.IntCollection
import combo.util.IntList
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min

interface Sentence : Iterable<Literal> {

    val literals: IntCollection
    val size get() = literals.size

    override fun iterator(): IntIterator = literals.iterator()

    fun isUnit(): Boolean = size == 1
    fun toDimacs(): String = toCnf().map { it.toDimacs() }.joinToString(separator = "\n")

    fun matches(l: Labeling): Int {
        // Not using Iterable<Int>.sumBy to avoid auto boxing
        var sum = 0
        for (it in literals.iterator()) {
            sum += if (l.asLiteral(it.asIx()) == it) 1 else 0
        }
        return sum
    }

    fun matchesUpdate(lit: Literal, oldMatches: Int) = oldMatches + if (lit in literals) 1 else -1
    fun flipsToSatisfy(matches: Int): Int

    fun flipsToSatisfy(l: Labeling): Int
    fun satisfies(l: Labeling) = flipsToSatisfy(l) == 0

    fun propagateUnit(unit: Literal): Sentence
    fun toCnf(): Sequence<Disjunction>

    /**
     * Reuses the clause if possible.
     */
    fun remap(remappedIds: IntArray): Sentence {
        val lits = literals.toArray()
        for ((i, l) in lits.withIndex()) {
            val remapped = remappedIds[lits[i].asIx()].asLiteral(l.asBoolean())
            require(remapped >= 0)
            literals.remove(lits[i])
            literals.add(remapped)
        }
        return this
    }
}

sealed class Clause(override val literals: IntCollection) : Sentence {
    abstract override fun propagateUnit(unit: Literal): Clause
}

object Tautology : Clause(IntList(0)) {
    override fun flipsToSatisfy(matches: Int) = 0
    override fun flipsToSatisfy(l: Labeling) = 0
    override fun propagateUnit(unit: Literal) = this
    override fun toCnf(): Sequence<Disjunction> = emptySequence()
    override fun toString() = "Tautology"
}

/*
 * (a || b || c)
 */
class Disjunction(literals: IntCollection) : Clause(literals) {

    override fun flipsToSatisfy(matches: Int) = if (matches > 0 || literals.isEmpty()) 0 else 1

    override fun flipsToSatisfy(l: Labeling): Int {
        for (lit in literals) {
            val ix = lit.asIx()
            if (l[ix] == lit.asBoolean())
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

    override fun toCnf() = sequenceOf(this)

    override fun toDimacs() = literals.joinToString(separator = " ", postfix = " 0") { it.asDimacs().toString() }
    override fun toString() = literals.joinToString(", ", "Disjunction(", ")") { it.toString() }
}

/**
 * a && b && c
 */
class Conjunction(literals: IntCollection) : Clause(literals) {

    override fun flipsToSatisfy(matches: Int) = literals.size - matches
    override fun flipsToSatisfy(l: Labeling): Int {
        var unmatched = 0
        for (lit in literals)
            if (l.asLiteral(lit.asIx()) != lit) unmatched++
        return unmatched
    }

    override fun propagateUnit(unit: Literal): Clause {
        if (!unit in literals) throw UnsatisfiableException(literal = unit)
        return this
    }

    override fun toCnf(): Sequence<Disjunction> = literals.asSequence().map { Disjunction(IntList(intArrayOf(it))) }

    override fun isUnit() = true
    override fun toString() = literals.joinToString(", ", "Conjunction(", ")") { it.toString() }
}

/**
 * literal <=> clause
 */
class Reified(val literal: Literal, val clause: Clause) : Sentence {

    init {
        if (clause.literals.isEmpty()) throw IllegalArgumentException("Empty clause.")
        if (literal in clause.literals || !literal in clause.literals)
            throw IllegalArgumentException("Literal appears in clause for reified.")
    }

    override val literals = clause.literals.copy().apply {
        add(literal)
    }

    override fun matches(l: Labeling): Int {
        val clauseMatches = clause.matches(l)
        return clauseMatches.asLiteral(l.asLiteral(literal.asIx()) == literal)
    }

    override fun matchesUpdate(lit: Literal, oldMatches: Int): Int {
        val oldClauseMatch = oldMatches.asIx()
        return if (literal.asIx() == lit.asIx()) {
            oldClauseMatch.asLiteral(lit == literal)
        } else {
            val oldLiteralMatch = oldMatches.asBoolean()
            clause.matchesUpdate(lit, oldClauseMatch).asLiteral(oldLiteralMatch)
        }
    }

    override fun flipsToSatisfy(matches: Int): Int {
        val clauseFlips = clause.flipsToSatisfy(matches.asIx())
        return if (matches.asBoolean()) min(1, clauseFlips)
        else return if (clauseFlips == 0) 1 else 0
    }

    override fun flipsToSatisfy(l: Labeling): Int {
        return if (l[literal.asIx()] == literal.asBoolean()) {
            min(1, clause.flipsToSatisfy(l))
        } else {
            // Make sure the clause CAN be negated
            when (clause) {
                is Disjunction -> {
                    if (clause.literals.any { l[it.asIx()] == it.asBoolean() }) 1
                    else 0
                }
                is Conjunction -> {
                    if (clause.literals.all { l[it.asIx()] == it.asBoolean() }) 1
                    else 0
                }
                is Tautology -> 1
            }
        }
    }

    override fun propagateUnit(unit: Literal): Sentence {
        return when {
            unit == literal -> clause
            unit.asIx() == literal.asIx() -> {
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

    override fun toCnf(): Sequence<Disjunction> {
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

    override fun remap(remappedIds: IntArray): Sentence {
        val reifiedLiteral = remappedIds[literal.asIx()].asLiteral(literal.asBoolean())
        return Reified(reifiedLiteral, clause.remap(remappedIds) as Clause)
    }

    override fun toString() = "Reified($literal, $clause)"
}

class Cardinality(override val literals: IntCollection, val degree: Int = 1, val operator: Operator = Operator.AT_MOST) : Sentence {

    init {
        for (l in literals)
            require(l.asBoolean()) {
                "Can only have non-negated literals in $this. " +
                        "Offending literal: $l"
            }
        if ((operator == Operator.AT_LEAST || operator == Operator.EXACTLY) && degree > literals.size)
            throw UnsatisfiableException("$this is not satisfiable (${literals.size} cannot be ${operator.operator}).")
    }

    enum class Operator(val operator: String) {
        AT_LEAST(">=") {
            override fun flipsToSatisfy(matches: Int, degree: Int) = max(0, degree - matches)
        },
        AT_MOST("<=") {
            override fun flipsToSatisfy(matches: Int, degree: Int) = max(0, matches - degree)
        },
        EXACTLY("=") {
            override fun flipsToSatisfy(matches: Int, degree: Int) =
                    max(AT_LEAST.flipsToSatisfy(matches, degree), AT_MOST.flipsToSatisfy(matches, degree))
        };

        abstract fun flipsToSatisfy(matches: Int, degree: Int): Int
    }

    override fun flipsToSatisfy(matches: Int) = operator.flipsToSatisfy(matches, degree)

    override fun flipsToSatisfy(l: Labeling): Int {
        var matches = 0
        for (lit in literals) {
            if (l.asLiteral(lit.asIx()) == lit) matches++
        }
        return operator.flipsToSatisfy(matches, degree)
    }

    override fun propagateUnit(unit: Literal): Sentence {
        val pos = unit in literals
        val neg = !unit in literals
        return if (pos || neg) {
            val copy = literals.copy().apply { if (pos) remove(unit) else remove(!unit) }
            val d = degree - if (pos) 1 else 0
            if (d <= 0) {
                if (copy.isEmpty() || operator == Operator.AT_LEAST) Tautology
                else Conjunction(copy.map { !it })
            } else if (d >= copy.size && operator == Operator.AT_MOST) Tautology
            else if (d > copy.size && operator != Operator.AT_MOST)
                throw UnsatisfiableException(
                        "$this is not satisfiable (${literals.size} cannot be ${operator.operator}).")
            else Cardinality(copy, d, operator)
        } else this
    }

    override fun toCnf(): Sequence<Disjunction> {
        if (operator != Operator.AT_MOST || degree != 1)
            TODO("Generating CNF clauses for cardinality constraint for operator other than <=1 is not implemented.")
        val array = literals.toArray()
        return array.indices.asSequence()
                .flatMap { i ->
                    (i + 1 until array.size).asSequence()
                            .map { j -> Disjunction(IntList(intArrayOf(!array[i], !array[j]))) }
                }
    }

    override fun toString() = literals.joinToString(", ", "Cardinality(", ") $operator $degree") { it.toString() }
}
