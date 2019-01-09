@file:JvmName("SearchStates")
@file:Suppress("NOTHING_TO_INLINE")

package combo.sat

import combo.math.IntPermutation
import combo.sat.solvers.LinearObjective
import combo.sat.solvers.ObjectiveFunction
import combo.util.IntList
import combo.util.IntSet
import combo.util.collectionOf
import combo.util.transformArray
import kotlin.jvm.JvmName
import kotlin.random.Random

/**
 * This contains cached information about satisfied sentences during search. The actual implementations
 * of [SearchState] are private, so initialize with the [SearchStateFactory]. Use either [SearchStateFactory.build]
 * method depending on whether the labeling should be initialized using a [ValueSelector] or it is pre-solved.
 */
interface SearchState {
    val labeling: MutableLabeling
    val totalUnsatisfied: Int
    val assumption: Conjunction
    fun randomUnsatisfied(rng: Random): Sentence
    fun flip(ix: Ix)

    /**
     * Returns the improvement in flipsToSatisfy. A positive improvement leads to a state that is close to a satisfiable
     * labeling.
     */
    fun improvement(ix: Ix): Int
}

interface SearchStateFactory {
    fun <O : ObjectiveFunction?> build(labeling: MutableLabeling, assumptions: Literals,
                                       valueSelector: ValueSelector<O>, function: O, rng: Random): SearchState

    fun build(labeling: MutableLabeling, assumptions: Literals): SearchState
}

interface ValueSelector<in O : ObjectiveFunction?> {
    fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: O): Boolean
}

object RandomSelector : ValueSelector<ObjectiveFunction?> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: ObjectiveFunction?) =
            rng.nextBoolean()
}

object WeightSelector : ValueSelector<LinearObjective> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: LinearObjective): Boolean =
            function.weights[ix] >= 0
}

object FalseSelector : ValueSelector<ObjectiveFunction?> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: ObjectiveFunction?) = false
}

object TrueSelector : ValueSelector<ObjectiveFunction?> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: ObjectiveFunction?) = true
}

class BasicSearchStateFactory(val problem: Problem) : SearchStateFactory {

    private val cardinalitySentences = problem.sentences.asSequence()
            .filter { it is Cardinality }.map { it as Cardinality }.toList().toTypedArray()

    override fun <O : ObjectiveFunction?> build(labeling: MutableLabeling, assumptions: Literals,
                                                valueSelector: ValueSelector<O>, function: O, rng: Random): SearchState {
        val state = BasicSearchState(labeling, problem, assumptions)
        if (valueSelector !is FalseSelector)
            for (i in 0 until labeling.size)
                labeling[i] = valueSelector.select(i, labeling, rng, function)
        labeling.setAll(assumptions)
        for (card in cardinalitySentences) {
            var matches = card.matches(state.labeling)
            val perm = card.literals.permutation(rng)
            if (card.operator != Cardinality.Operator.AT_LEAST) {
                // operator: <= or ==
                while (matches > card.degree) {
                    val lit = perm.nextInt()
                    if (state.labeling[lit.asIx()]) {
                        state.labeling.flip(lit.asIx())
                        matches--
                    }
                }
            }
            if (card.operator != Cardinality.Operator.AT_MOST) {
                // operator: >= or ==
                while (matches < card.degree) {
                    val lit = perm.nextInt()
                    if (!state.labeling[lit.asIx()]) {
                        state.labeling.flip(lit.asIx())
                        matches++
                    }
                }
            }
        }
        for ((sentId, sent) in problem.sentences.withIndex()) {
            state.matches[sentId] = sent.matches(labeling)
            state.totalUnsatisfied += sent.flipsToSatisfy(state.matches[sentId]).also {
                if (it > 0) state.unsatisfied.add(sentId)
            }
        }
        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)
        return state
    }

    override fun build(labeling: MutableLabeling, assumptions: Literals): SearchState {
        val state = BasicSearchState(labeling, problem, assumptions)
        for ((sentId, sent) in problem.sentences.withIndex()) {
            state.matches[sentId] = sent.matches(labeling)
            if (sent.flipsToSatisfy(state.matches[sentId]) > 0) state.unsatisfied.add(sentId)
        }
        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)
        for (lit in assumptions) if (labeling.asLiteral(lit.asIx()) != lit) state.flip(lit.asIx())
        return state
    }
}

/**
 * Caches the result of sentence evaluation by keeping an int per sentence with the number of matching literals.
 */
private class BasicSearchState(override val labeling: MutableLabeling, val problem: Problem, assumptions: Literals) : SearchState {

    override var totalUnsatisfied: Int = 0

    val unsatisfied = IntSet()
    val matches = IntArray(problem.nbrSentences + 1)
    override val assumption = Conjunction(collectionOf(assumptions))
    private val assumptionIxs = collectionOf(assumption.literals.toArray().apply { transformArray { it.asIx() } })

    override fun randomUnsatisfied(rng: Random): Sentence {
        val sentId = unsatisfied.random(rng)
        return if (sentId == matches.lastIndex) assumption
        else problem.sentences[sentId]
    }

    override fun improvement(ix: Ix): Int {
        val literal = !labeling.asLiteral(ix)
        val assumptionImprovement =
                if (literal.asIx() in assumptionIxs) improvementSentence(literal, assumption, matches.lastIndex)
                else 0
        return assumptionImprovement + problem.sentencesWith(ix).sumBy { sentId ->
            val sent = problem.sentences[sentId]
            improvementSentence(literal, sent, sentId)
        }
    }

    private inline fun improvementSentence(literal: Literal, sent: Sentence, sentId: Int): Int {
        val oldFlips = sent.flipsToSatisfy(matches[sentId])
        val newMatches = sent.matchesUpdate(literal, matches[sentId])
        val newFlips = sent.flipsToSatisfy(newMatches)
        return oldFlips - newFlips
    }

    override fun flip(ix: Ix) {
        labeling.flip(ix)
        val literal = labeling.asLiteral(ix)
        if (literal.asIx() in assumptionIxs)
            flipSentence(literal, assumption, matches.lastIndex)
        for (sentId in problem.sentencesWith(ix)) {
            val sent = problem.sentences[sentId]
            flipSentence(literal, sent, sentId)
        }
    }

    private inline fun flipSentence(literal: Literal, sent: Sentence, sentId: Int) {
        val oldFlips = sent.flipsToSatisfy(matches[sentId])
        matches[sentId] = sent.matchesUpdate(literal, matches[sentId])
        val newFlips = sent.flipsToSatisfy(labeling)
        if (oldFlips > 0 && newFlips == 0) unsatisfied.remove(sentId)
        else if (newFlips > 0 && oldFlips == 0) unsatisfied.add(sentId)
        totalUnsatisfied += newFlips - oldFlips
    }
}

class FixedSearchState(override val labeling: MutableLabeling) : SearchState {
    override val assumption: Conjunction
        get() = Conjunction(IntList(IntArray(0)))

    override fun randomUnsatisfied(rng: Random) = Tautology
    override val totalUnsatisfied: Int
        get() = 0

    override fun flip(ix: Ix) = throw UnsupportedOperationException()
    override fun improvement(ix: Ix) = 0
}

class PropSearchStateFactory(val problem: Problem, val propGraph: BinaryPropagationGraph) : SearchStateFactory {

    private val cardinalitySentences = (0 until problem.nbrSentences).asSequence()
            .filter { problem.sentences[it] is Cardinality }.filter { it in propGraph.complexSentences }
            .toList().toIntArray()

    override fun <O : ObjectiveFunction?> build(labeling: MutableLabeling, assumptions: Literals, valueSelector: ValueSelector<O>, function: O, rng: Random): SearchState {
        val state = PropSearchState(labeling, problem, propGraph, assumptions)

        for (ix in IntPermutation(labeling.size, rng)) {
            val lit = ix.asLiteral(valueSelector.select(ix, labeling, rng, function))
            labeling.set(lit)
            labeling.setAll(propGraph.literalPropagations[lit])
        }
        labeling.setAll(assumptions)
        assumptions.forEach {
            labeling.setAll(propGraph.literalPropagations[it])
        }

        for (sentId in propGraph.complexSentences) {
            val sent = problem.sentences[sentId]
            val sentMatches = sent.matches(labeling)
            if (sentMatches > 0) state.matches[sentId] = sentMatches
            state.totalUnsatisfied += sent.flipsToSatisfy(sentMatches).also {
                if (it > 0) state.unsatisfied.add(sentId)
            }
        }

        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)

        for (card in cardinalitySentences) {
            val sent = problem.sentences[card] as Cardinality
            val perm = sent.literals.permutation(rng)
            if (sent.operator != Cardinality.Operator.AT_LEAST) {
                // operator: <= or ==
                while (state.matches[card] > sent.degree && perm.hasNext()) {
                    val lit = perm.nextInt()
                    if (state.labeling[lit.asIx()])
                        state.flip(lit.asIx())
                }
            }
            if (sent.operator != Cardinality.Operator.AT_MOST) {
                // operator: >= or ==
                while (state.matches[card] < sent.degree && perm.hasNext()) {
                    val lit = perm.nextInt()
                    if (!state.labeling[lit.asIx()])
                        state.flip(lit.asIx())
                }
            }
        }

        return state
    }

    override fun build(labeling: MutableLabeling, assumptions: Literals): SearchState {
        val state = PropSearchState(labeling, problem, propGraph, assumptions)
        for (sentId in propGraph.complexSentences) {
            val sent = problem.sentences[sentId]
            state.matches[sentId] = sent.matches(labeling)
            if (sent.flipsToSatisfy(state.matches[sentId]) > 0) state.unsatisfied.add(sentId)
        }
        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)
        for (lit in assumptions) if (labeling.asLiteral(lit.asIx()) != lit) state.flip(lit.asIx())
        return state
    }
}

/**
 * Uses information from a [BinaryPropagationGraph]
 */
private class PropSearchState(override val labeling: MutableLabeling,
                              val problem: Problem,
                              val propGraph: BinaryPropagationGraph,
                              assumptions: Literals) : SearchState {

    override var totalUnsatisfied: Int = 0

    val unsatisfied = IntSet()
    val matches = IntArray(problem.nbrSentences + 1) // TODO use IntMap ???
    override val assumption = Conjunction(collectionOf(assumptions))

    override fun randomUnsatisfied(rng: Random): Sentence {
        val sentId = unsatisfied.random(rng)
        return if (sentId == matches.lastIndex) assumption
        else problem.sentences[sentId]
    }

    override fun flip(ix: Ix) {
        val literal = !labeling.asLiteral(ix)
        flipSentence(literal, assumption, matches.lastIndex)
        propGraph.variableSentences[ix].forEach { sentId ->
            val sent = problem.sentences[sentId]
            flipSentence(literal, sent, sentId)
        }
        labeling.set(literal)
        labeling.setAll(propGraph.literalPropagations[literal])
    }

    private inline fun flipSentence(literal: Literal, sent: Sentence, sentId: Int) {
        var matchUpdate = if (literal in sent.literals || !literal in sent.literals)
            sent.matchesUpdate(literal, matches[sentId]) else matches[sentId]
        for (lit in propGraph.literalPropagations[literal]) {
            if (lit != labeling.asLiteral(lit.asIx()) && (lit in sent.literals || !lit in sent.literals))
                matchUpdate = sent.matchesUpdate(lit, matchUpdate)
        }
        val newFlips = sent.flipsToSatisfy(matchUpdate)
        val oldFlips = sent.flipsToSatisfy(matches[sentId])
        matches[sentId] = matchUpdate
        if (oldFlips > 0 && newFlips == 0) unsatisfied.remove(sentId)
        else if (newFlips > 0 && oldFlips == 0) unsatisfied.add(sentId)
        totalUnsatisfied += newFlips - oldFlips
    }

    override fun improvement(ix: Ix): Int {
        val literal = !labeling.asLiteral(ix)
        return improvementSentence(literal, assumption, matches.lastIndex) +
                propGraph.variableSentences[ix].sumBy { sentId ->
                    val sent = problem.sentences[sentId]
                    improvementSentence(literal, sent, sentId)
                }
    }

    private inline fun improvementSentence(literal: Literal, sent: Sentence, sentId: Int): Int {
        var matchUpdate = if (literal in sent.literals || !literal in sent.literals)
            sent.matchesUpdate(literal, matches[sentId]) else matches[sentId]
        for (lit in propGraph.literalPropagations[literal]) {
            if (lit != labeling.asLiteral(lit.asIx()) && (lit in sent.literals || !lit in sent.literals))
                matchUpdate = sent.matchesUpdate(lit, matchUpdate)
        }
        val newFlips = sent.flipsToSatisfy(matchUpdate)
        val oldFlips = sent.flipsToSatisfy(matches[sentId])
        return oldFlips - newFlips
    }
}

