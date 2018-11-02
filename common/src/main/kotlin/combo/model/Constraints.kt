@file:JvmName("Constraints")

package combo.model

import combo.sat.*
import combo.util.remove
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

interface SentenceBuilder {
    fun toSentences(ri: ReferenceIndex): Array<Sentence>
    val references: Array<out Reference>
}

class ReferenceIndex(features: Array<out Feature<*>>) {

    val index: Map<Reference, Ix>
    val nbrVariables: Int

    init {
        var ix = 0
        index = features.associate {
            val oldIx = ix
            ix += it.nbrLiterals
            it to oldIx
        }
        nbrVariables = ix
    }

    fun indexOf(refs: Array<out Reference>): Literals =
            IntArray(refs.size) { i ->
                refs[i].toLiteral(indexOf(refs[i]))
            }.apply { sort() }

    fun indexOf(ref: Reference): Ix =
            index[ref.rootFeature] ?: throw ValidationException("Could not find feature $ref")
}

abstract class BaseSentenceBuilder : SentenceBuilder {
    abstract operator fun not(): BaseSentenceBuilder
}

sealed class ClauseBuilder : BaseSentenceBuilder() {
    abstract fun toClause(fi: ReferenceIndex): Clause
    override fun toSentences(ri: ReferenceIndex): Array<Sentence> = arrayOf(toClause(ri))
}

abstract class Reference : ClauseBuilder() {
    override operator fun not(): Reference = Not(this)
    override fun toClause(fi: ReferenceIndex) = Tautology

    abstract fun toLiteral(ix: Ix): Literal
    abstract val rootFeature: Feature<*>
}

class DisjunctionBuilder(override val references: Array<out Reference>) : ClauseBuilder() {
    override fun toClause(fi: ReferenceIndex): Clause {
        // Also performs simplification caused by AndBuilder#pullIn and #distribute
        var literals = fi.indexOf(references)
        for (i in 1 until literals.size)
            if (literals[i].asIx() == literals[i - 1].asIx())
                if (literals[i] == literals[i - 1])
                    literals = literals.remove(i)
                else return Tautology
        return when {
            literals.isEmpty() -> Tautology
            literals.size == 1 -> Conjunction(intArrayOf(literals[0]))
            else -> Disjunction(literals)
        }.apply { validate() }
    }

    override operator fun not(): ConjunctionBuilder =
            ConjunctionBuilder(references.asSequence().map { !it }.toList().toTypedArray())

    override fun toString() = "Or(${references.joinToString()})"
}

class ConjunctionBuilder(override val references: Array<out Reference>) : ClauseBuilder() {
    override fun toClause(fi: ReferenceIndex) =
            if (references.isEmpty()) Tautology
            else Conjunction(fi.indexOf(references))
                    .apply { validate() }

    override operator fun not(): DisjunctionBuilder =
            DisjunctionBuilder(references.asSequence().map { !it }.toList().toTypedArray())

    override fun toString() = "And(${references.joinToString()})"

}

infix fun BaseSentenceBuilder.or(sent: BaseSentenceBuilder) = or(this, sent)
infix fun BaseSentenceBuilder.and(sent: BaseSentenceBuilder) = and(this, sent)
infix fun BaseSentenceBuilder.implies(sent: BaseSentenceBuilder) = !this or sent
infix fun BaseSentenceBuilder.equivalent(sent: BaseSentenceBuilder) = (this implies sent) and (sent implies this)
infix fun BaseSentenceBuilder.xor(sent: BaseSentenceBuilder) = (this or sent) and (!this or !sent)

infix fun Reference.or(ref: Reference): DisjunctionBuilder = or(this, ref)
infix fun Reference.and(ref: Reference): ConjunctionBuilder = and(this, ref)

infix fun DisjunctionBuilder.or(ref: Reference) =
        DisjunctionBuilder(Array(references.size + 1) {
            if (it in references.indices) references[it]
            else ref
        })

infix fun ConjunctionBuilder.and(ref: Reference) =
        ConjunctionBuilder(Array(references.size + 1) {
            if (it in references.indices) references[it]
            else ref
        })

fun or(vararg sents: BaseSentenceBuilder): BaseSentenceBuilder {
    val references = ArrayList<Reference>()
    val ands = ArrayList<CnfBuilder>()
    for (sent in sents) {
        when (sent) {
            is Reference -> references.add(sent)
            is DisjunctionBuilder -> references.addAll(sent.references)
            is ConjunctionBuilder -> ands.add(CnfBuilder(sent))
            is CnfBuilder -> ands.add(sent)
        }
    }
    val or = DisjunctionBuilder(references.toTypedArray())
    return if (ands.isEmpty()) {
        or
    } else {
        var result = CnfBuilder(ands[0].disjunctions)
        for (i in 1 until ands.size)
            result = result.distribute(ands[i])
        result.pullIn(or)
    }
}

fun and(vararg sents: BaseSentenceBuilder): BaseSentenceBuilder {
    if (sents.size <= 1) {
        return or(*sents)
    }
    val ors = ArrayList<DisjunctionBuilder>()
    for (sent in sents) {
        when (sent) {
            is Reference -> ors.add(DisjunctionBuilder(arrayOf(sent)))
            is DisjunctionBuilder -> ors.add(sent)
            is ConjunctionBuilder -> ors.addAll(CnfBuilder(sent).disjunctions)
            is CnfBuilder -> ors.addAll(sent.disjunctions)
        }
    }
    return CnfBuilder(ors.toTypedArray())
}

fun and(vararg refs: Reference): ConjunctionBuilder = ConjunctionBuilder(refs)
fun or(vararg refs: Reference): DisjunctionBuilder = DisjunctionBuilder(refs)

fun or(refs: Iterable<Reference>) : DisjunctionBuilder = DisjunctionBuilder(refs.asSequence().toList().toTypedArray())
fun and(refs: Iterable<Reference>) : ConjunctionBuilder = ConjunctionBuilder(refs.asSequence().toList().toTypedArray())

@JvmOverloads
fun atMost(refs: Iterable<Reference>, degree: Int = 1) =
        CardinalityBuilder(refs.toList().toTypedArray(), degree, Cardinality.Operator.AT_MOST)

@JvmOverloads
fun atLeast(refs: Iterable<Reference>, degree: Int = 1) =
        CardinalityBuilder(refs.toList().toTypedArray(), degree, Cardinality.Operator.AT_LEAST)

fun excludes(refs: Iterable<Reference>) = atMost(refs, degree = 1)
@JvmOverloads
fun exactly(refs: Iterable<Reference>, degree: Int = 1) =
        CardinalityBuilder(refs.toList().toTypedArray(), degree, Cardinality.Operator.EXACTLY)

@JvmOverloads
fun atMost(vararg refs: Reference, degree: Int = 1) = CardinalityBuilder(refs, degree, Cardinality.Operator.AT_MOST)
@JvmOverloads
fun atLeast(vararg refs: Reference, degree: Int = 1) = CardinalityBuilder(refs, degree, Cardinality.Operator.AT_LEAST)
fun excludes(vararg refs: Reference) = atMost(*refs, degree = 1)
@JvmOverloads
fun exactly(vararg refs: Reference, degree: Int = 1) = CardinalityBuilder(refs, degree, Cardinality.Operator.EXACTLY)

class CardinalityBuilder(override val references: Array<out Reference>,
                         val degree: Int = 1,
                         val operator: Cardinality.Operator = Cardinality.Operator.AT_MOST) : SentenceBuilder {

    override fun toSentences(ri: ReferenceIndex): Array<Sentence> =
            arrayOf(with(ri.indexOf(references)) {
                if (size > 1 || (size == 1 && operator == Cardinality.Operator.EXACTLY || operator == Cardinality.Operator.AT_LEAST))
                    Cardinality(this, degree, operator)
                else Tautology
            }.apply { validate() })

    override fun toString() = "Cardinality(${references.joinToString()}, $operator, $degree)"
}

infix fun Reference.reified(clause: ClauseBuilder) = object : SentenceBuilder {
    override val references: Array<out Reference>
        get() = Array(clause.references.size + 1) { i -> if (i == 0) this@reified else clause.references[i - 1] }

    override fun toSentences(ri: ReferenceIndex): Array<Sentence> =
            arrayOf(Reified(this@reified.toLiteral(ri.indexOf(this@reified)), clause.toClause(ri))
                    .apply { validate() })

    override fun toString() = "Reified(${this@reified}, $clause)"
}

private class Not(private val wrap: Reference) : Reference() {
    override operator fun not() = wrap
    override fun toLiteral(ix: Ix) = !(wrap.toLiteral(ix))
    override val rootFeature get() = wrap.rootFeature
    override val references get():Array<out Reference> = wrap.references
    override fun toString(): String = "Not($wrap)"
}


private class CnfBuilder(val disjunctions: Array<DisjunctionBuilder>) : BaseSentenceBuilder() {
    override val references: Array<out Reference>
        get() = disjunctions.asSequence().flatMap { it.references.asSequence() }.toList().toTypedArray()

    constructor(conjunction: ConjunctionBuilder) : this(
            conjunction.references.map { DisjunctionBuilder(arrayOf(it)) }.toTypedArray())

    fun pullIn(to: DisjunctionBuilder) =
            CnfBuilder(Array(disjunctions.size) { i ->
                DisjunctionBuilder(Array(disjunctions[i].references.size + to.references.size) { j ->
                    if (j in disjunctions[i].references.indices) disjunctions[i].references[j]
                    else to.references[j - disjunctions[i].references.size]
                })
            })

    fun distribute(cnf: CnfBuilder): CnfBuilder {
        val totalOrs = ArrayList<DisjunctionBuilder>()
        for (o1 in disjunctions)
            for (o2 in cnf.disjunctions)
                totalOrs.add(DisjunctionBuilder(Array(o1.references.size + o2.references.size) {
                    if (it in o1.references.indices) o1.references[it]
                    else o2.references[it - o1.references.size]
                }))
        return CnfBuilder(totalOrs.toTypedArray())
    }

    override fun toSentences(ri: ReferenceIndex) =
            disjunctions.asSequence()
                    .flatMap { it.toSentences(ri).asSequence() }
                    .filter { it !== Tautology }
                    .toList().toTypedArray()
                    .also { it.forEach { sent -> sent.validate() } }

    override operator fun not() =
            disjunctions.asSequence()
                    .map { !it }.map { CnfBuilder(it) }
                    .reduce { a: CnfBuilder, b: CnfBuilder -> a.distribute(b) }

    override fun toString() = "CNF(${disjunctions.joinToString()})"
}


