package combo.sat

import combo.math.IntPermutation
import combo.model.UnsatisfiableException
import combo.util.HashIntSet
import combo.util.IntSet
import kotlin.random.Random

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
