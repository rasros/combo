@file:JvmName("ConstraintsBuilder")

package combo.model

import combo.sat.*
import combo.sat.Relation.*
import combo.util.IntCollection
import combo.util.IntList
import combo.util.collectionOf
import combo.util.remove
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

interface ConstraintBuilder {
    fun toConstraints(ri: ReferenceIndex): Array<Constraint>
    val variables: Array<out Variable>
    operator fun not(): ConstraintBuilder
}

class ReferenceIndex(features: Array<out Feature<*>>) {

    private val index: Map<Variable, Ix>
    internal val nbrVariables: Int

    init {
        var ix = 0
        index = features.associate {
            val oldIx = ix
            ix += it.nbrLiterals
            it to oldIx
        }
        nbrVariables = ix
    }

    internal fun indexOf(vars: Array<out Variable>): IntCollection {
        var array = IntArray(vars.size) { i ->
            vars[i].toLiteral(indexOf(vars[i]))
        }.apply { sort() }
        var offset = 0
        for (i in 1 until array.size)
            if (array[i - offset].toIx() == array[i - 1 - offset].toIx())
                if (array[i - offset] == array[i - 1 - offset]) {
                    array = array.remove(i - offset)
                    offset++
                } else return IntList(0)
        return collectionOf(array)
    }

    internal fun indexOf(varible: Variable): Ix =
            index[varible.rootFeature] ?: throw NoSuchElementException("Could not find feature $varible")
}

sealed class ClauseBuilder : ConstraintBuilder {
    abstract fun toClause(fi: ReferenceIndex): Clause
    override fun toConstraints(ri: ReferenceIndex): Array<Constraint> = arrayOf(toClause(ri))
    abstract override fun not(): ClauseBuilder
}

abstract class Variable : ClauseBuilder() {
    override operator fun not(): Variable = Not(this)
    override fun toClause(fi: ReferenceIndex) = Tautology

    abstract fun toLiteral(ix: Ix): Literal
    abstract val rootFeature: Feature<*>
}

class DisjunctionBuilder(override val variables: Array<out Variable>) : ClauseBuilder() {
    override fun toClause(fi: ReferenceIndex): Clause {
        val literals = fi.indexOf(variables)
        return when {
            literals.isEmpty() -> Tautology
            literals.size == 1 -> Conjunction(literals)
            else -> Disjunction(literals)
        }
    }

    override operator fun not(): ConjunctionBuilder =
            ConjunctionBuilder(variables.asSequence().map { !it }.toList().toTypedArray())

    override fun toString() = "Multiple(${variables.joinToString()})"
}

class ConjunctionBuilder(override val variables: Array<out Variable>) : ClauseBuilder() {
    override fun toClause(fi: ReferenceIndex): Clause {
        val literals = fi.indexOf(variables)
        return if (literals.isEmpty()) Tautology
        else Conjunction(literals)
    }

    override operator fun not(): DisjunctionBuilder =
            DisjunctionBuilder(variables.asSequence().map { !it }.toList().toTypedArray())

    override fun toString() = "And(${variables.joinToString()})"

}

infix fun ClauseBuilder.or(cons: ClauseBuilder) = or(this, cons)
infix fun ClauseBuilder.and(cons: ClauseBuilder) = and(this, cons)
infix fun ClauseBuilder.implies(cons: ClauseBuilder) = !this or cons
infix fun ClauseBuilder.equivalent(cons: ClauseBuilder) = (this implies cons) and (cons implies this)
infix fun ClauseBuilder.xor(cons: ClauseBuilder) = (this or cons) and (!this or !cons)

infix fun Variable.or(variable: Variable): DisjunctionBuilder = or(this, variable)
infix fun Variable.and(variable: Variable): ConjunctionBuilder = and(this, variable)

infix fun DisjunctionBuilder.or(variable: Variable) =
        DisjunctionBuilder(Array(variables.size + 1) {
            if (it in variables.indices) variables[it]
            else variable
        })

infix fun ConjunctionBuilder.and(variable: Variable) =
        ConjunctionBuilder(Array(variables.size + 1) {
            if (it in variables.indices) variables[it]
            else variable
        })

fun or(vararg constraints: ClauseBuilder): ClauseBuilder {
    val variables = ArrayList<Variable>()
    val ands = ArrayList<CnfBuilder>()
    for (cons in constraints) {
        when (cons) {
            is Variable -> variables.add(cons)
            is DisjunctionBuilder -> variables.addAll(cons.variables)
            is ConjunctionBuilder -> ands.add(CnfBuilder(cons))
            is CnfBuilder -> ands.add(cons)
        }
    }
    val or = DisjunctionBuilder(variables.toTypedArray())
    return if (ands.isEmpty()) {
        or
    } else {
        var result = CnfBuilder(ands[0].disjunctions)
        for (i in 1 until ands.size)
            result = result.distribute(ands[i])
        result.pullIn(or)
    }
}

fun and(vararg constraints: ClauseBuilder): ClauseBuilder {
    if (constraints.size <= 1) {
        return or(*constraints)
    }
    val disjunctions = ArrayList<DisjunctionBuilder>()
    for (c in constraints) {
        when (c) {
            is Variable -> disjunctions.add(DisjunctionBuilder(arrayOf(c)))
            is DisjunctionBuilder -> disjunctions.add(c)
            is ConjunctionBuilder -> disjunctions.addAll(CnfBuilder(c).disjunctions)
            is CnfBuilder -> disjunctions.addAll(c.disjunctions)
        }
    }
    return CnfBuilder(disjunctions.toTypedArray())
}

fun and(vararg variables: Variable): ConjunctionBuilder = ConjunctionBuilder(variables)
fun or(vararg variables: Variable): DisjunctionBuilder = DisjunctionBuilder(variables)

fun or(variables: Iterable<Variable>): DisjunctionBuilder = DisjunctionBuilder(variables.asSequence().toList().toTypedArray())
fun and(variables: Iterable<Variable>): ConjunctionBuilder = ConjunctionBuilder(variables.asSequence().toList().toTypedArray())

@JvmOverloads
fun atMost(variables: Iterable<Variable>, degree: Int = 1) =
        CardinalityBuilder(variables.toList().toTypedArray(), degree, LE)

@JvmOverloads
fun atLeast(variables: Iterable<Variable>, degree: Int = 1) =
        CardinalityBuilder(variables.toList().toTypedArray(), degree, GE)

fun excludes(variables: Iterable<Variable>) = atMost(variables, degree = 1)
@JvmOverloads
fun exactly(variables: Iterable<Variable>, degree: Int = 1) =
        CardinalityBuilder(variables.toList().toTypedArray(), degree, EQ)

@JvmOverloads
fun atMost(vararg variables: Variable, degree: Int = 1) = CardinalityBuilder(variables, degree, LE)

@JvmOverloads
fun atLeast(vararg variables: Variable, degree: Int = 1) = CardinalityBuilder(variables, degree, GE)

fun excludes(vararg variables: Variable) = atMost(*variables, degree = 1)
@JvmOverloads
fun exactly(vararg variables: Variable, degree: Int = 1) = CardinalityBuilder(variables, degree, EQ)

class CardinalityBuilder(override val variables: Array<out Variable>,
                         val degree: Int = 1,
                         val relation: Relation = LE) : ConstraintBuilder {

    override fun toConstraints(ri: ReferenceIndex): Array<Constraint> {
        val literals = ri.indexOf(variables)
        val cons: Constraint = if (literals.size == 2 && relation == GE)
            Disjunction(literals)
        else if (literals.size == 2 && relation == LE)
            Disjunction(literals.map { !it })
        else if (literals.size > 1 || (literals.size == 1 && relation == EQ || relation == GE))
            Cardinality(literals, degree, relation)
        else Tautology
        return arrayOf(cons)
    }

    override fun not() = CardinalityBuilder(variables, degree, relation.not())

    /*
    // TODO verify that above works instead of this here:
    override fun toSentences(ri: ReferenceIndex): Array<Constraint> =
            arrayOf(with(ri.indexOf(variables)) {
                if (size > 1 || (size == 1 && relation == Cardinality.Relation.EQ || relation == Cardinality.Relation.GE))
                    Cardinality(this, degree, relation)
                else Tautology
            })*/

    override fun toString() = "Cardinality(${variables.joinToString()}, $relation, $degree)"
}

class ReifiedBuilder(val variable: Variable, val clause: ClauseBuilder) : ConstraintBuilder {

    override val variables: Array<out Variable>
        get() = Array(clause.variables.size + 1) { i -> if (i == 0) variable else clause.variables[i - 1] }

    override fun toConstraints(ri: ReferenceIndex): Array<Constraint> {
        val c = clause.toClause(ri)
        val lit = variable.toLiteral(ri.indexOf(variable))
        return if (c is Tautology) arrayOf(Conjunction(IntList(intArrayOf(lit))))
        else arrayOf(Reified(lit, c))
    }

    override fun not() = ReifiedBuilder(!variable, clause)
    override fun toString() = "Reified(${variable}, $clause)"
}

infix fun Variable.reified(clause: ClauseBuilder) = ReifiedBuilder(this, clause)

class Not(private val variable: Variable) : Variable() {
    override operator fun not() = variable
    override fun toLiteral(ix: Ix) = !(variable.toLiteral(ix))
    override val rootFeature get() = variable.rootFeature
    override val variables get():Array<out Variable> = variable.variables
    override fun toString(): String = "Not($variable)"
}

class CnfBuilder(val disjunctions: Array<DisjunctionBuilder>) : ClauseBuilder() {

    override val variables: Array<out Variable>
        get() = disjunctions.asSequence().flatMap { it.variables.asSequence() }.toList().toTypedArray()

    constructor(conjunction: ConjunctionBuilder) : this(
            conjunction.variables.map { DisjunctionBuilder(arrayOf(it)) }.toTypedArray())

    fun pullIn(to: DisjunctionBuilder) =
            CnfBuilder(Array(disjunctions.size) { i ->
                DisjunctionBuilder(Array(disjunctions[i].variables.size + to.variables.size) { j ->
                    if (j in disjunctions[i].variables.indices) disjunctions[i].variables[j]
                    else to.variables[j - disjunctions[i].variables.size]
                })
            })

    fun distribute(cnf: CnfBuilder): CnfBuilder {
        val totalOrs = ArrayList<DisjunctionBuilder>()
        for (o1 in disjunctions)
            for (o2 in cnf.disjunctions)
                totalOrs.add(DisjunctionBuilder(Array(o1.variables.size + o2.variables.size) {
                    if (it in o1.variables.indices) o1.variables[it]
                    else o2.variables[it - o1.variables.size]
                }))
        return CnfBuilder(totalOrs.toTypedArray())
    }

    override fun toConstraints(ri: ReferenceIndex) =
            disjunctions.asSequence()
                    .flatMap { it.toConstraints(ri).asSequence() }
                    .filter { it !== Tautology }
                    .toList().toTypedArray()

    override operator fun not() =
            disjunctions.asSequence()
                    .map { !it }.map { CnfBuilder(it) }
                    .reduce { a: CnfBuilder, b: CnfBuilder -> a.distribute(b) }

    override fun toString() = "CNF(${disjunctions.joinToString()})"

    override fun toClause(fi: ReferenceIndex) = throw UnsupportedOperationException()
}


