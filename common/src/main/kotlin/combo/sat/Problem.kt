package combo.sat

import combo.math.IntPermutation
import combo.model.UnsatisfiableException
import combo.util.HashIntSet
import combo.util.IntSet
import kotlin.random.Random

class Problem(val sentences: Array<out Sentence>, val nbrVariables: Int) {

    val nbrSentences get() = sentences.size

    val varToSent: Array<IntArray> = Array(nbrVariables) { IntArray(2) }.apply {
        val sizes = IntArray(nbrVariables)
        for ((i, clause) in sentences.withIndex()) {
            for (lit in clause) {
                val id = lit.asIx()
                if (this[id].size == sizes[id]) {
                    this[id] = this[id].copyOf(sizes[id] * 2)
                }
                this[id][sizes[id]++] = i
            }
        }
        for (i in sizes.indices)
            this[i] = this[i].copyOf(sizes[i])
    }

    fun sentencesWith(varIx: Ix) = varToSent[varIx]

    fun satisfies(l: Labeling, s: Labeling? = null) = sentences.all { it.satisfies(l, s) }

    fun unitPropagation(units: IntSet = HashIntSet()): Array<Sentence> {

        fun addUnit(units: IntSet, unit: Literal): Boolean {
            if (units.contains(!unit)) throw UnsatisfiableException("Unsatisfiable by unit propagation.", literal = unit)
            else return units.add(unit)
        }

        val unitsIx = ArrayList<Int>()
        if (units.isNotEmpty())
            unitsIx.add(sentences.size + 1)

        val initial = Conjunction(units.toArray().apply { sort() })
        for (i in sentences.indices)
            if (sentences[i].isUnit()) {
                unitsIx.add(i)
                for (l in sentences[i]) addUnit(units, l)
            }

        val copy = Array<Sentence?>(sentences.size) { null }

        while (!unitsIx.isEmpty()) {
            val clauseId = unitsIx.removeAt(0)
            val clause = if (clauseId >= sentences.size) initial else copy[clauseId] ?: sentences[clauseId]
            for (unitLit in clause.literals) {
                val unitId = unitLit.asIx()
                val matching = sentencesWith(unitId)
                for (i in matching.indices) {
                    val reduced = (copy[matching[i]] ?: sentences[matching[i]]).propagateUnit(unitLit)
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.literals.any { l -> addUnit(units, l) }) unitsIx.add(matching[i])
                }
            }
        }
        return copy.asSequence()
                .mapIndexed { i, it -> it ?: sentences[i] }
                .filter { !it.isUnit() && it != Tautology }
                .toList()
                .toTypedArray()
    }

    fun simplify(units: IntSet = HashIntSet(), addConjunction: Boolean = false): Problem {
        var reduced = unitPropagation(units)
        if (addConjunction && units.isNotEmpty()) reduced += Conjunction(units.toArray().apply { sort() })
        return Problem(reduced, nbrVariables)
    }
}

class ExtendedProblem(val problem: Problem, exactPropagation: Boolean = false) {

    val literalPropagations: Array<IntArray>
    val literalSentences: Array<IntArray>

    init {
        if (exactPropagation) {
            val preImplications = Array(problem.nbrVariables * 2) { HashIntSet(16) }

            for (sent in problem.sentences) {
                if (sent is Disjunction && sent.size == 2) {
                    preImplications[!sent.literals[0]].add(sent.literals[1])
                    preImplications[!sent.literals[1]].add(sent.literals[0])
                } else if (sent is Cardinality && sent.degree == 1 &&
                        (sent.operator == Cardinality.Operator.AT_MOST || sent.operator == Cardinality.Operator.EXACTLY)) {
                    for (i in sent.literals.indices) {
                        for (j in (i + 1) until sent.literals.size) {
                            preImplications[sent.literals[i]].add(!sent.literals[j])
                            preImplications[sent.literals[j]].add(!sent.literals[i])
                        }
                    }
                } else if (sent is Reified) {
                    if (sent.clause is Disjunction) {
                        for (clauseLit in sent.clause.literals)
                            preImplications[!sent.literal].add(!clauseLit)
                    } else if (sent.clause is Conjunction) {
                        for (clauseLit in sent.clause.literals)
                            preImplications[sent.literal].add(clauseLit)
                    }
                }
            }

            for (imps in preImplications)
                for (ilit in imps)
                    imps.addAll(preImplications[ilit]) // TODO efficient propagate looping

            literalPropagations = Array(problem.nbrVariables * 2) { i ->
                preImplications[i].toArray().apply { sort() }
            }
        } else {
            literalPropagations = Array(problem.nbrVariables * 2) {
                val set = HashIntSet()
                set.add(it)
                try {
                    problem.unitPropagation(set)
                } catch (ignored: UnsatisfiableException) {
                }
                set.remove(it)
                set.toArray()
            }
        }

        literalSentences = Array(problem.nbrVariables * 2) { i ->
            val sentences = HashIntSet()
            sentences.addAll(problem.sentencesWith(i.asIx()))
            literalPropagations[i].forEach { j ->
                sentences.addAll(problem.sentencesWith(j))
            }
            sentences.toArray()
        }
    }

    inner class LabelingTracker(val context: IntArray,
                                val labeling: MutableLabeling,
                                val unsatisfied: IntSet = HashIntSet(),
                                rng: Random) {

        init {
            val setLabeling = BitFieldLabeling(labeling.size)

            run {
                fun setLiteral(literal: Literal): Boolean {
                    if (!setLabeling[literal.asIx()]) {
                        setLabeling[literal.asIx()] = true
                        labeling.set(literal)
                        return true
                    }
                    return false
                }

                val contextSentences = HashIntSet()
                context.forEach {
                    contextSentences.addAll(literalSentences[it])
                    if (!setLiteral(it) ||
                            !literalPropagations[it].all {
                                contextSentences.addAll(literalSentences[it])
                                setLiteral(it)
                            })
                        throw UnsatisfiableException("Unsatisfiable by unit propagation due to context literals ${context.joinToString()}.")
                }
                if (!contextSentences.all { problem.sentences[it].satisfies(labeling, setLabeling) })
                    throw UnsatisfiableException("Unsatisfiable by unit propagation due to context literals ${context.joinToString()}.")
            }

            val flipped = HashIntSet()

            for (ix in IntPermutation(labeling.size, rng).iterator()) {
                if (setLabeling[ix]) continue
                setLabeling[ix] = true
                val literal = ix.asLiteral(rng.nextBoolean())
                if (literalPropagations[literal].any { setLabeling[it.asIx()] && it != labeling.asLiteral(it.asIx()) }) {
                    set(!literal, null, setLabeling)
                } else {
                    set(literal, flipped, setLabeling)
                    if (!literalSentences[labeling.asLiteral(ix)].all { sentId ->
                                sentId in unsatisfied || problem.sentences[sentId].satisfies(labeling, setLabeling)
                            }) {
                        undo(flipped, setLabeling)
                        set(!literal, null, setLabeling)
                    }
                }
                notify(ix, setLabeling)
            }
        }

        fun notify(ix: Ix, setLabeling: Labeling? = null) {
            literalSentences[labeling.asLiteral(ix)].forEach { sentId ->
                if (sentId !in unsatisfied && !problem.sentences[sentId].satisfies(labeling, setLabeling))
                    unsatisfied.add(sentId)
            }
        }

        fun set(literal: Literal, affected: IntSet? = null, setLabeling: MutableLabeling? = null) {
            val ix = literal.asIx()
            if (affected != null && (labeling.asLiteral(ix) != literal || setLabeling != null)) {
                affected.add(ix)
                setLabeling?.set(ix, true)
            }
            if (setLabeling == null || !setLabeling[ix]) labeling.set(literal)
            for (i in literalPropagations[literal]) {
                val l = literalPropagations[literal][i]
                if (setLabeling != null && setLabeling[l.asIx()]) continue
                else {
                    setLabeling?.set(l.asIx(), true)
                    affected?.add(l.asIx())
                }
                if (labeling.asLiteral(l.asIx()) != l) {
                    labeling.flip(l.asIx())
                    affected?.add(l.asIx())
                }
            }
        }

        fun undo(affected: IntSet, setLabeling: MutableLabeling? = null) {
            affected.forEach {
                labeling.flip(it)
                if (setLabeling != null) setLabeling[it] = false
            }
            affected.clear()
        }
    }
}
