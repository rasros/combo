package combo.sat

import combo.model.UnsatisfiableException
import combo.model.ValidationException
import combo.util.applyTransform
import combo.util.remove
import kotlin.math.max
import kotlin.math.min

interface Sentence : Iterable<Literal> {

    val literals: Literals
    val size get() = literals.size

    override fun iterator() = literals.iterator()

    fun validate() = literals.validate()
    fun isUnit(): Boolean = size == 1
    fun toDimacs(): String = toCnf().map { it.toDimacs() }.joinToString(separator = "\n")

    fun flipsToSatisfy(l: Labeling, s: Labeling? = null): Int
    fun satisfies(l: Labeling, s: Labeling? = null) = flipsToSatisfy(l, s) == 0

    fun propagateUnit(unit: Literal): Sentence
    fun toCnf(): Sequence<Disjunction>

    /**
     * Reuses the clause if possible.
     */
    fun remap(remappedIds: IntArray): Sentence {
        for ((i, l) in literals.withIndex()) {
            literals[i] = remappedIds[literals[i].asIx()].asLiteral(l.asBoolean())
            require(literals[i] >= 0)
        }
        return this
    }

}

sealed class Clause(override val literals: Literals) : Sentence {
    abstract override fun propagateUnit(unit: Literal): Clause
}

object Tautology : Clause(IntArray(0)) {
    override fun flipsToSatisfy(l: Labeling, s: Labeling?) = 0
    override fun propagateUnit(unit: Literal) = this
    override fun toCnf(): Sequence<Disjunction> = emptySequence()
    override fun toString() = "Tautology"
}

/*
 * (a || b || c)
 */
class Disjunction(literals: Literals) : Clause(literals) {
    override fun flipsToSatisfy(l: Labeling, s: Labeling?): Int {
        for (lit in literals) {
            val id = lit.asIx()
            if ((s != null && !s[id]) || l[id] == lit.asBoolean())
                return 0
        }
        return 1
    }

    override fun propagateUnit(unit: Literal): Clause {
        for ((i, l) in literals.withIndex()) {
            if (l.asIx() == unit.asIx()) {
                return when {
                    unit == l -> Tautology
                    literals.size == 1 -> throw UnsatisfiableException(literal = l)
                    else -> literals.remove(i).let {
                        if (it.size == 1) Conjunction(it)
                        else Disjunction(it)
                    }.also { validate() }
                }
            }
        }
        return this
    }

    override fun toCnf() = sequenceOf(this)

    override fun toDimacs() = literals.joinToString(separator = " ", postfix = " 0") { it.asDimacs().toString() }
    override fun toString() = literals.joinToString(", ", "Disjunction(", ")") { it.toString() }
}

/**
 * a && b && c
 */
class Conjunction(literals: Literals) : Clause(literals) {
    override fun flipsToSatisfy(l: Labeling, s: Labeling?): Int {
        var unmatched = 0
        for (lit in literals) {
            if ((s == null || s[lit.asIx()]) && l.asLiteral(lit.asIx()) != lit) unmatched++
        }
        return unmatched
    }

    override fun propagateUnit(unit: Literal): Clause {
        val id = unit.asIx()
        for (lit in literals)
            if (id == lit.asIx() && unit != lit)
                throw UnsatisfiableException(literal = lit)
        return this
    }

    override fun toCnf(): Sequence<Disjunction> = literals.asSequence().map { Disjunction(intArrayOf(it)) }

    override fun isUnit() = true
    override fun toString() = literals.joinToString(", ", "Conjunction(", ")") { it.toString() }
}

/**
 * literal <=> clause
 */
class Reified(val literal: Literal, val clause: Clause) : Sentence {

    override fun validate() {
        super.validate()
        clause.validate()
    }

    override val literals = ((clause.literals + literal).apply { sort() })

    override fun flipsToSatisfy(l: Labeling, s: Labeling?): Int {
        return if (s != null && !s[literal.asIx()]) 0
        else if (l[literal.asIx()] == literal.asBoolean()) {
            min(1, clause.flipsToSatisfy(l, s))
        } else {
            // Make sure the clause CAN be negated
            when (clause) {
                is Disjunction -> {
                    if (clause.literals.any {
                                (s == null || s[it.asIx()]) && l[it.asIx()] == it.asBoolean()
                            })
                        1
                    else 0
                }
                is Conjunction -> {
                    if (clause.literals.all {
                                (s == null || s[it.asIx()]) && l[it.asIx()] == it.asBoolean()
                            })
                        1
                    else 0
                }
                is Tautology -> 1
            }
        }
    }

    override fun propagateUnit(unit: Literal): Sentence {
        return when {
            unit == literal -> return clause
            unit.asIx() == literal.asIx() -> {
                val negated = clause.literals.copyOf().applyTransform { !it }
                when (clause) {
                    is Disjunction -> Conjunction(negated)
                    is Conjunction -> Disjunction(negated)
                    is Tautology -> throw UnsatisfiableException("The model is unsatisfiable by unit propagation, " +
                            "there is a contradiction in the specification.", literal = unit)
                }
            }
            else -> {
                val propagatedClause = clause.propagateUnit(unit)
                when (propagatedClause) {
                    clause -> this
                    is Tautology -> Conjunction(intArrayOf(literal))
                    else -> Reified(literal, propagatedClause)
                }
            }
        }
    }

    override fun toCnf(): Sequence<Disjunction> {
        return when (clause) {
            is Disjunction -> {
                val c1 = clause.literals.asSequence().map { Disjunction(intArrayOf(literal, !it).apply { this.sort() }) }
                val c2 = sequenceOf(Disjunction((clause.literals + !literal).apply { this.sort() }))
                c1 + c2
            }
            is Conjunction -> {
                val c1 = clause.literals.asSequence().map { Disjunction(intArrayOf(!literal, it).apply { this.sort() }) }
                val c2 = sequenceOf(Disjunction((clause.literals.copyOf().applyTransform { !it } + literal).apply { this.sort() }))
                c1 + c2
            }
            is Tautology -> sequenceOf(Disjunction(intArrayOf(literal)))
        }
    }

    override fun remap(remappedIds: IntArray): Sentence {
        val reifiedLiteral = remappedIds[literal.asIx()].asLiteral(literal.asBoolean())
        return Reified(reifiedLiteral, clause.remap(remappedIds) as Clause)
    }

    override fun toString() = "Reified($literal, $clause)"
}

class Cardinality(override val literals: Literals, val degree: Int = 1, val operator: Operator = Operator.AT_MOST) : Sentence {

    enum class Operator(val operator: String) {
        AT_LEAST(">=") {
            override fun satisfies(matches: Int, unset: Int, degree: Int) = max(0, degree - matches - unset)
        },
        AT_MOST("<=") {
            override fun satisfies(matches: Int, unset: Int, degree: Int) = max(0, matches - degree)
        },
        EXACTLY("=") {
            override fun satisfies(matches: Int, unset: Int, degree: Int) =
                    max(AT_LEAST.satisfies(matches, unset, degree), AT_MOST.satisfies(matches, unset, degree))
        };

        abstract fun satisfies(matches: Int, unset: Int, degree: Int): Int
    }

    override fun flipsToSatisfy(l: Labeling, s: Labeling?): Int {
        var matches = 0
        var unset = 0
        for (lit in literals) {
            if (s != null && !s[lit.asIx()]) unset++
            else if (l.asLiteral(lit.asIx()) == lit) matches++
        }
        return operator.satisfies(matches, unset, degree)
    }

    override fun validate() {
        super.validate()
        for (l in literals)
            if (!l.asBoolean())
                throw ValidationException("Can only have non-negated literals in $this. " +
                        "Offending literal: $l")
        if ((operator == Operator.AT_LEAST || operator == Operator.EXACTLY) && degree > literals.size)
            throw UnsatisfiableException("$this is not satisfiable (${literals.size} cannot be ${operator.operator}).")
    }

    override fun propagateUnit(unit: Literal): Sentence {
        for ((i, lit) in literals.withIndex()) {
            if (lit.asIx() == unit.asIx()) {
                val copy = literals.remove(i)
                val d = if (unit == literals[i]) degree - 1 else degree
                return if (d <= 0) {
                    if (copy.isEmpty() || operator == Operator.AT_LEAST) Tautology
                    else Conjunction(copy.applyTransform { !it })
                } else if (d >= copy.size && operator == Operator.AT_MOST) Tautology
                else if (d > copy.size && operator != Operator.AT_MOST)
                    throw UnsatisfiableException(
                            "$this is not satisfiable (${literals.size} cannot be ${operator.operator}).")
                else Cardinality(copy, d, operator)
            }
        }
        return this
    }

    override fun toCnf(): Sequence<Disjunction> {
        if (operator != Operator.AT_MOST || degree != 1)
            TODO("Generating CNF clauses for cardinality constraint for operator other than <=1 is not implemented.")
        return literals.indices.asSequence()
                .flatMap { i ->
                    (i + 1 until literals.size).asSequence()
                            .map { j -> Disjunction(intArrayOf(!literals[i], !literals[j])) }
                }
    }

    override fun toString() = literals.joinToString(", ", "Cardinality(", ") $operator $degree") { it.toString() }
}
