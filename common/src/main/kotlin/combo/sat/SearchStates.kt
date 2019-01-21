@file:JvmName("SearchStates")
@file:Suppress("NOTHING_TO_INLINE")

package combo.sat

import combo.math.IntPermutation
import combo.sat.solvers.LinearObjective
import combo.sat.solvers.ObjectiveFunction
import combo.util.*
import kotlin.jvm.JvmName
import kotlin.random.Random

/**
 * This contains cached information about satisfied constraints during search. The actual implementations
 * of [SearchState] are private, so initialize with the [SearchStateFactory]. Use either [SearchStateFactory.build]
 * method depending on whether the labeling should be initialized using a [ValueSelector] or it is pre-solved.
 */
interface SearchState {
    val labeling: MutableLabeling
    val totalUnsatisfied: Int
    val assumption: Conjunction
    fun randomUnsatisfied(rng: Random): Constraint
    fun flip(ix: Ix)
    fun changes(ix: Ix): Literals = EMPTY_INT_ARRAY

    /**
     * Returns the improvement in flipsToSatisfy. A positive improvement leads to a state that is close to a satisfiable
     * labeling.
     */
    fun improvement(ix: Ix): Int
}

/**
 * Creates search states.
 */
interface SearchStateFactory {
    fun <O : ObjectiveFunction?> build(labeling: MutableLabeling, assumptions: Literals,
                                       valueSelector: ValueSelector<O>, function: O, rng: Random): SearchState

    fun build(labeling: MutableLabeling, assumptions: Literals): SearchState
}

/**
 * Initializes values during search.
 */
interface ValueSelector<in O : ObjectiveFunction?> {
    fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: O): Boolean
}

object RandomSelector : ValueSelector<ObjectiveFunction?> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: ObjectiveFunction?) =
            rng.nextBoolean()
}

object WeightSelector : ValueSelector<LinearObjective> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: LinearObjective): Boolean =
            if (function.maximize) function.weights[ix] >= 0
            else function.weights[ix] < 0
}

object FalseSelector : ValueSelector<ObjectiveFunction?> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: ObjectiveFunction?) = false
}

object TrueSelector : ValueSelector<ObjectiveFunction?> {
    override fun select(ix: Ix, labeling: MutableLabeling, rng: Random, function: ObjectiveFunction?) = true
}

class BasicSearchStateFactory(val problem: Problem) : SearchStateFactory {

    private val cardinalitySentences = problem.constraints.asSequence()
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
            if (card.relation != Relation.GE) {
                // relation: <= or ==
                while (matches > card.degree) {
                    val lit = perm.nextInt()
                    if (state.labeling[lit.toIx()]) {
                        state.labeling.flip(lit.toIx())
                        matches--
                    }
                }
            }
            if (card.relation != Relation.LE) {
                // relation: >= or ==
                while (matches < card.degree) {
                    val lit = perm.nextInt()
                    if (!state.labeling[lit.toIx()]) {
                        state.labeling.flip(lit.toIx())
                        matches++
                    }
                }
            }
        }
        for ((sentId, sent) in problem.constraints.withIndex()) {
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
        for ((sentId, sent) in problem.constraints.withIndex()) {
            state.matches[sentId] = sent.matches(labeling)
            if (sent.flipsToSatisfy(state.matches[sentId]) > 0) state.unsatisfied.add(sentId)
        }
        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)
        for (lit in assumptions) if (labeling.literal(lit.toIx()) != lit) state.flip(lit.toIx())
        return state
    }
}

/**
 * Caches the result of sentence evaluation by keeping an int per sentence with the number of matching literals.
 */
private class BasicSearchState(override val labeling: MutableLabeling, val problem: Problem, assumptions: Literals) : SearchState {

    override var totalUnsatisfied: Int = 0

    val unsatisfied = IntSet()
    val matches = IntArray(problem.nbrConstraints + 1)
    override val assumption = Conjunction(collectionOf(assumptions))
    private val assumptionIxs = collectionOf(assumption.literals.toArray().apply { transformArray { it.toIx() } })

    override fun randomUnsatisfied(rng: Random): Constraint {
        val sentId = unsatisfied.random(rng)
        return if (sentId == matches.lastIndex) assumption
        else problem.constraints[sentId]
    }

    override fun improvement(ix: Ix): Int {
        val literal = !labeling.literal(ix)
        val assumptionImprovement =
                if (literal.toIx() in assumptionIxs) improvementSentence(literal, assumption, matches.lastIndex)
                else 0
        return assumptionImprovement + problem.constraintsWith(ix).sumBy { sentId ->
            val sent = problem.constraints[sentId]
            improvementSentence(literal, sent, sentId)
        }
    }

    private inline fun improvementSentence(literal: Literal, sent: Constraint, sentId: Int): Int {
        val oldFlips = sent.flipsToSatisfy(matches[sentId])
        val newMatches = sent.matchesUpdate(literal, matches[sentId])
        val newFlips = sent.flipsToSatisfy(newMatches)
        return oldFlips - newFlips
    }

    override fun flip(ix: Ix) {
        labeling.flip(ix)
        val literal = labeling.literal(ix)
        if (literal.toIx() in assumptionIxs)
            flipSentence(literal, assumption, matches.lastIndex)
        for (sentId in problem.constraintsWith(ix)) {
            val sent = problem.constraints[sentId]
            flipSentence(literal, sent, sentId)
        }
    }

    private inline fun flipSentence(literal: Literal, sent: Constraint, sentId: Int) {
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

/**
 * Uses information from a binary disjunctions propagations.
 */
class PropSearchStateFactory(val problem: Problem) : SearchStateFactory {

    private val literalPropagations: Array<Literals>
    private val variableSentences: Array<IntArray>
    private val complexSentences = IntSet()

    private val assumptionPenalty = 1000

    private val cardinalitySentences = (0 until problem.nbrConstraints).asSequence()
            .filter { problem.constraints[it] is Cardinality }.filter { it in complexSentences }
            .toList().toIntArray()

    init {
        val implicationSets = Array(problem.nbrVariables * 2) { IntSet(2) }

        for ((sentId, sent) in problem.constraints.withIndex()) {
            if (sent is Disjunction && sent.size == 2) {
                val array = sent.literals.toArray()
                implicationSets[!array[0]].add(array[1])
                implicationSets[!array[1]].add(array[0])
            } else if (sent is Cardinality && sent.degree == 1 && sent.relation == Relation.LE) {
                val array = sent.literals.toArray()
                for (i in array.indices) {
                    for (j in (i + 1) until array.size) {
                        implicationSets[array[i]].add(!array[j])
                        implicationSets[array[j]].add(!array[i])
                    }
                }
            }
            /*else if (sent is Reified) {
                complexSentences.add(sentId) // TODO investigate cases where not necessary
                if (sent.clause is Disjunction) {
                    for (clauseLit in sent.clause.literals) {
                        implicationSets[!sent.literal].add(!clauseLit)
                        implicationSets[clauseLit].add(sent.literal)
                    }
                } else if (sent.clause is Conjunction) {
                // TODO at least this one
                    for (clauseLit in sent.clause.literals) {
                        implicationSets[sent.literal].add(clauseLit)
                        implicationSets[clauseLit].add(sent.literal)
                        implicationSets[!clauseLit].add(!sent.literal)
                        implicationSets[!sent.literal].add(!clauseLit)
                    }
                }
            }*/ else complexSentences.add(sentId)
        }

        // Adds transitive implications like: a -> b -> c
        // Ie. transitive closure
        val inverseGraph = Array(problem.nbrVariables * 2) { IntSet(2) }
        for (i in 0 until problem.nbrVariables * 2) {
            for (j in implicationSets[i]) inverseGraph[j].add(i)
        }

        val queue = IntSet().apply {
            addAll(0 until problem.nbrVariables * 2)
        }

        while (queue.isNotEmpty()) {
            val lit = queue.first()
            var dirty = false
            for (i in implicationSets[lit].toArray()) {
                if (implicationSets[lit].addAll(implicationSets[i])) {
                    dirty = true
                    queue.addAll(inverseGraph[lit])
                    for (j in implicationSets[lit]) inverseGraph[j].add(lit)
                    inverseGraph[i].addAll(implicationSets[lit])
                }
            }
            if (!dirty) queue.remove(lit)
        }

        for (i in 0 until problem.nbrVariables)
            if (i in implicationSets[i])
                throw UnsatisfiableException("Unsatisfiable by krom 2-sat.", literal = i)

        literalPropagations = Array(problem.nbrVariables * 2) { i ->
            implicationSets[i].toArray()
        }

        val literalSentences = Array(problem.nbrVariables * 2) { i ->
            val sentences = IntSet()
            for (sentId in problem.constraintsWith(i.toIx()))
                if (sentId in complexSentences) sentences.add(sentId)
            //constraints.addAll(problem.sentencesWith(i.asIx()))
            literalPropagations[i].forEach { j ->
                for (sentId in problem.constraintsWith(j.toIx()))
                    if (sentId in complexSentences) sentences.add(sentId)
                //constraints.addAll(problem.sentencesWith(j.asIx()))
            }
            sentences.toArray()
        }

        variableSentences = Array(problem.nbrVariables) { i ->
            val sentences = IntSet()
            sentences.addAll(literalSentences[i.toLiteral(true)])
            sentences.addAll(literalSentences[i.toLiteral(false)])
            sentences.toArray()
        }
    }


    override fun <O : ObjectiveFunction?> build(labeling: MutableLabeling, assumptions: Literals, valueSelector: ValueSelector<O>, function: O, rng: Random): SearchState {
        val state = PropSearchState(labeling, problem, assumptions)

        for (ix in IntPermutation(labeling.size, rng)) {
            val lit = ix.toLiteral(valueSelector.select(ix, labeling, rng, function))
            labeling.set(lit)
            labeling.setAll(literalPropagations[lit])
        }
        labeling.setAll(assumptions)
        assumptions.forEach {
            labeling.setAll(literalPropagations[it])
        }

        for (sentId in complexSentences) {
            val sent = problem.constraints[sentId]
            val sentMatches = sent.matches(labeling)
            if (sentMatches > 0) state.matches[sentId] = sentMatches
            state.totalUnsatisfied += sent.flipsToSatisfy(sentMatches).also {
                if (it > 0) state.unsatisfied.add(sentId)
            }
        }

        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips*assumptionPenalty
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)

        for (card in cardinalitySentences) {
            val sent = problem.constraints[card] as Cardinality
            val perm = sent.literals.permutation(rng)
            if (sent.relation != Relation.GE) {
                // relation: <= or ==
                while (state.matches[card] > sent.degree && perm.hasNext()) {
                    val lit = perm.nextInt()
                    if (state.labeling[lit.toIx()])
                        state.flip(lit.toIx())
                }
            }
            if (sent.relation != Relation.LE) {
                // relation: >= or ==
                while (state.matches[card] < sent.degree && perm.hasNext()) {
                    val lit = perm.nextInt()
                    if (!state.labeling[lit.toIx()])
                        state.flip(lit.toIx())
                }
            }
        }

        return state
    }

    override fun build(labeling: MutableLabeling, assumptions: Literals): SearchState {
        val state = PropSearchState(labeling, problem, assumptions)
        for (sentId in complexSentences) {
            val sent = problem.constraints[sentId]
            state.matches[sentId] = sent.matches(labeling)
            if (sent.flipsToSatisfy(state.matches[sentId]) > 0) state.unsatisfied.add(sentId)
        }
        state.matches[state.matches.lastIndex] = state.assumption.matches(labeling)
        val assumptionFlips = state.assumption.flipsToSatisfy(state.matches.last())
        state.totalUnsatisfied += assumptionFlips
        if (assumptionFlips > 0) state.unsatisfied.add(state.matches.lastIndex)
        for (lit in assumptions) if (labeling.literal(lit.toIx()) != lit) state.flip(lit.toIx())
        return state
    }

    private inner class PropSearchState(override val labeling: MutableLabeling,
                                        val problem: Problem,
                                        assumptions: Literals) : SearchState {

        override var totalUnsatisfied: Int = 0

        val unsatisfied = IntSet()
        val matches = IntArray(problem.nbrConstraints + 1) // TODO use IntMap ???
        override val assumption = Conjunction(collectionOf(assumptions))

        override fun randomUnsatisfied(rng: Random): Constraint {
            val sentId = unsatisfied.random(rng)
            return if (sentId == matches.lastIndex) assumption
            else problem.constraints[sentId]
        }

        override fun flip(ix: Ix) {
            val literal = !labeling.literal(ix)
            flipSentence(literal, assumption, matches.lastIndex, assumptionPenalty)
            variableSentences[ix].forEach { sentId ->
                val sent = problem.constraints[sentId]
                flipSentence(literal, sent, sentId)
            }
            labeling.set(literal)
            labeling.setAll(literalPropagations[literal])
        }

        override fun changes(ix: Ix) = literalPropagations[!labeling.literal(ix)]

        private inline fun flipSentence(literal: Literal, sent: Constraint, sentId: Int, weight: Int = 1) {
            var matchUpdate = if (literal in sent.literals || !literal in sent.literals)
                sent.matchesUpdate(literal, matches[sentId]) else matches[sentId]
            for (lit in literalPropagations[literal]) {
                if (lit != labeling.literal(lit.toIx()) && (lit in sent.literals || !lit in sent.literals))
                    matchUpdate = sent.matchesUpdate(lit, matchUpdate)
            }
            val newFlips = sent.flipsToSatisfy(matchUpdate) * weight
            val oldFlips = sent.flipsToSatisfy(matches[sentId]) * weight
            matches[sentId] = matchUpdate
            if (oldFlips > 0 && newFlips == 0) unsatisfied.remove(sentId)
            else if (newFlips > 0 && oldFlips == 0) unsatisfied.add(sentId)
            totalUnsatisfied += newFlips - oldFlips
        }

        override fun improvement(ix: Ix): Int {
            val literal = !labeling.literal(ix)
            return improvementSentence(literal, assumption, matches.lastIndex, assumptionPenalty) +
                    variableSentences[ix].sumBy { sentId ->
                        val sent = problem.constraints[sentId]
                        improvementSentence(literal, sent, sentId)
                    }
        }

        private inline fun improvementSentence(literal: Literal, sent: Constraint, sentId: Int, weight: Int = 1): Int {
            var matchUpdate = if (literal in sent.literals || !literal in sent.literals)
                sent.matchesUpdate(literal, matches[sentId]) else matches[sentId]
            for (lit in literalPropagations[literal]) {
                if (lit != labeling.literal(lit.toIx()) && (lit in sent.literals || !lit in sent.literals))
                    matchUpdate = sent.matchesUpdate(lit, matchUpdate)
            }
            val newFlips = sent.flipsToSatisfy(matchUpdate) * weight
            val oldFlips = sent.flipsToSatisfy(matches[sentId]) * weight
            return oldFlips - newFlips
        }
    }
}