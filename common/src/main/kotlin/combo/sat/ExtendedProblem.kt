package combo.sat

import combo.math.IntPermutation
import combo.model.UnsatisfiableException
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntSet
import kotlin.random.Random

class ExtendedProblem(val problem: Problem, exactPropagation: Boolean = false) {

    val literalPropagations: Array<IntArray>
    val literalSentences: Array<IntArray>

    init {
        if (!exactPropagation) {
            val implicationSets = Array(problem.nbrVariables * 2) { IntSet() }

            for (sent in problem.sentences) {
                if (sent is Disjunction && sent.size == 2) {
                    implicationSets[!sent.literals[0]].add(sent.literals[1])
                    implicationSets[!sent.literals[1]].add(sent.literals[0])
                } else if (sent is Cardinality && sent.degree == 1 &&
                        (sent.operator == Cardinality.Operator.AT_MOST || sent.operator == Cardinality.Operator.EXACTLY)) {
                    for (i in sent.literals.indices) {
                        for (j in (i + 1) until sent.literals.size) {
                            implicationSets[sent.literals[i]].add(!sent.literals[j])
                            implicationSets[sent.literals[j]].add(!sent.literals[i])
                        }
                    }
                } else if (sent is Reified) {
                    if (sent.clause is Disjunction) {
                        for (clauseLit in sent.clause.literals)
                            implicationSets[!sent.literal].add(!clauseLit)
                    } else if (sent.clause is Conjunction) {
                        for (clauseLit in sent.clause.literals)
                            implicationSets[sent.literal].add(clauseLit)
                    }
                }
            }

            for (imps in implicationSets)
                for (ilit in imps.toArray()) // toArray to avoid concurrent modification
                    imps.addAll(implicationSets[ilit]) // TODO efficient propagate looping

            literalPropagations = Array(problem.nbrVariables * 2) { i ->
                implicationSets[i].toArray()
            }
        } else {
            literalPropagations = Array(problem.nbrVariables * 2) {
                val set = IntSet()
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
            val sentences = IntSet()
            sentences.addAll(problem.sentencesWith(i.asIx()))
            literalPropagations[i].forEach { j ->
                sentences.addAll(problem.sentencesWith(j.asIx()))
            }
            sentences.toArray()
        }
    }

    inner class LabelingTracker(val labeling: MutableLabeling,
                                val context: IntArray = EMPTY_INT_ARRAY,
                                val unsatisfied: IntSet = IntSet(),
                                rng: Random = Random) {


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

                val contextSentences = IntSet()
                context.forEach {
                    contextSentences.addAll(literalSentences[it])
                    if (!setLiteral(it) ||
                            !literalPropagations[it].all {
                                contextSentences.addAll(literalSentences[it])
                                setLiteral(it)
                            })
                        throw UnsatisfiableException(
                                "Unsatisfiable by unit propagation due to context literals ${context.joinToString()}.")
                }
                if (!contextSentences.all { problem.sentences[it].satisfies(labeling, setLabeling) })
                    throw UnsatisfiableException(
                            "Unsatisfiable by unit propagation due to context literals ${context.joinToString()}.")
            }

            val affected = IntSet()

            for (ix in IntPermutation(labeling.size, rng).iterator()) {
                if (setLabeling[ix]) continue
                val literal = ix.asLiteral(rng.nextBoolean())
                if (literalPropagations[literal].any { setLabeling[it.asIx()] && it != labeling.asLiteral(it.asIx()) }) {
                    set(!literal, null, setLabeling)
                } else {
                    if (set(literal, affected, setLabeling)) {
                        undoSet(affected, setLabeling)
                        set(!literal, null, setLabeling)
                    } else affected.clear()
                }
            }
        }

        fun set(literal: Literal, affected: IntSet? = null, setLabeling: MutableLabeling? = null): Boolean {
            val ix = literal.asIx()
            if (labeling.asLiteral(ix) != literal || (setLabeling != null && !setLabeling[ix]))
                affected?.add(ix)
            setLabeling?.set(ix, true)
            labeling.set(literal)
            for (l in literalPropagations[literal]) {
                val lix = l.asIx()
                if (setLabeling != null && setLabeling[lix]) continue
                else {
                    setLabeling?.set(lix, true)
                    affected?.add(lix)
                }
                if (labeling.asLiteral(lix) != l) {
                    labeling.flip(lix)
                    affected?.add(lix)
                }
            }
            return literalSentences[literal].fold(true) { all: Boolean, sentId: Int ->
                val sat = problem.sentences[sentId].satisfies(labeling, setLabeling)
                if (sat) unsatisfied.remove(sentId)
                else unsatisfied.add(sentId)
                sat && all
            }
        }

        fun undoSet(affected: IntSet, setLabeling: MutableLabeling? = null) {
            affected.forEach {
                labeling.flip(it)
                setLabeling?.set(it, false)
            }
            affected.clear()
        }
    }
}
