package combo.sat

import combo.math.IntPermutation
import combo.util.IntSet
import kotlin.random.Random

interface LabelingTracker {
    val labeling: MutableLabeling
    val unsatisfied: IntSet
    fun set(literal: Literal)
    fun undo(literal: Literal)
    fun updateUnsatisfied(literal: Literal): Boolean
}

interface ValueSelector {
    fun value(ix: Int): Boolean
}

class RandomSelector(val rng: Random) : ValueSelector {
    override fun value(ix: Int) = rng.nextBoolean()
}

class WeightSelector(val weights: DoubleArray, val rng: Random) : ValueSelector {
    override fun value(ix: Int) = weights[ix] >=0
}

class FlipLabelingTracker(override val labeling: MutableLabeling,
                          val problem: Problem,
                          assumptions: Literals,
                          valueSelector: ValueSelector) : LabelingTracker {
    override val unsatisfied = IntSet()

    init {
        for (i in 0 until labeling.size)
            labeling[i] = valueSelector.value(i)

        labeling.setAll(assumptions)
        for ((i, s) in problem.sentences.withIndex()) {
            if (!s.satisfies(labeling)) {
                unsatisfied.add(i)
            }
        }
    }

    override fun set(literal: Literal) = labeling.set(literal)
    override fun undo(literal: Literal) = labeling.set(!literal)
    override fun updateUnsatisfied(literal: Literal) =
            problem.sentencesWith(literal.asIx()).fold(true) { all, i ->
                problem.sentences[i].satisfies(labeling).also {
                    if (it) unsatisfied.remove(i)
                    else unsatisfied.add(i)
                } && all
            }
}

class PropLabelingTracker(override val labeling: MutableLabeling,
                          val problem: Problem,
                          val propTable: UnitPropagationTable,
                          assumptions: IntArray,
                          rng: Random,
                          valueSelector: ValueSelector) : LabelingTracker {

    override val unsatisfied = IntSet()
    val affected = IntSet()

    init {
        val setLabeling = BitFieldLabeling(labeling.size)

        run {
            fun setLiteral(literal: Literal): Boolean {
                return if (!setLabeling[literal.asIx()]) {
                    setLabeling[literal.asIx()] = true
                    labeling.set(literal)
                    true
                } else labeling.asLiteral(literal.asIx()) == literal
            }

            val assumptionSentences = IntSet()
            assumptions.forEach {
                assumptionSentences.addAll(propTable.literalSentences[it])
                if (!setLiteral(it) ||
                        !propTable.literalPropagations[it].all {
                            assumptionSentences.addAll(propTable.literalSentences[it])
                            setLiteral(it)
                        })
                    throw UnsatisfiableException(
                            "Unsatisfiable by unit propagation due to assumptions literals ${assumptions.joinToString()}.")
            }
            if (!assumptionSentences.all { problem.sentences[it].satisfies(labeling, setLabeling) })
                throw UnsatisfiableException(
                        "Unsatisfiable by unit propagation due to assumptions literals ${assumptions.joinToString()}.")
        }

        val affected = IntSet()

        for (ix in IntPermutation(labeling.size, rng).iterator()) {
            if (setLabeling[ix]) continue
            val literal = ix.asLiteral(valueSelector.value(ix))
            if (propTable.literalPropagations[literal].any { setLabeling[it.asIx()] && it != labeling.asLiteral(it.asIx()) }) {
                set(!literal, null, setLabeling)
                update(!literal, setLabeling)
            } else {
                set(literal, affected, setLabeling)
                if (update(literal, setLabeling)) affected.clear()
                else {
                    undo(literal, setLabeling)
                    set(!literal, null, setLabeling)
                    update(!literal, setLabeling)
                }
            }
        }
    }

    override fun set(literal: Literal) = set(literal, affected, null)

    private fun set(literal: Literal, affected: IntSet?, setLabeling: MutableLabeling?) {
        val ix = literal.asIx()
        setLabeling?.set(ix, true)
        labeling.set(literal)
        for (l in propTable.literalPropagations[literal]) {
            val lix = l.asIx()
            if (setLabeling != null) {
                if (setLabeling[lix]) continue
                else {
                    setLabeling[lix] = true
                    labeling.set(l)
                    affected?.add(lix)
                }
            } else if (labeling.asLiteral(lix) != l) {
                labeling.flip(lix)
                affected?.add(lix)
            }
        }
    }

    override fun updateUnsatisfied(literal: Literal) = update(literal, null)

    private fun update(literal: Literal, setLabeling: MutableLabeling?): Boolean {
        affected.clear()
        return propTable.literalSentences[literal].fold(true) { all: Boolean, sentId: Int ->
            val sat = problem.sentences[sentId].satisfies(labeling, setLabeling)
            if (sat) unsatisfied.remove(sentId)
            else unsatisfied.add(sentId)
            sat && all
        }
    }

    override fun undo(literal: Literal) = undo(literal, null)

    private fun undo(literal: Literal, setLabeling: MutableLabeling?) {
        labeling.set(!literal)
        setLabeling?.set(literal.asIx(), false)
        affected.forEach {
            labeling.flip(it)
            setLabeling?.set(it, false)
        }
        affected.clear()
    }
}