package combo.sat.solvers

import combo.math.IntPermutation
import combo.sat.*
import combo.util.IntSet
import combo.util.millis
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

sealed class LocalSearch(val problem: Problem,
                         val propTable: UnitPropagationTable?,
                         val timeout: Long,
                         val maxRestarts: Int,
                         val maxSteps: Int,
                         val maxSidewaySteps: Int,
                         val pRandomWalk: Double,
                         val maxConsideration: Int) {
    abstract val config: SolverConfig

    var totalSuccesses: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set
    var totalIterations: Long = 0
        private set
    var totalFlips: Long = 0
        private set

    var latestSequenceLiterals: MutableList<Int>? = null
    var latestSequenceSentence: MutableList<Sentence>? = null

    protected fun recordCompleted(satisfied: Boolean) {
        if (satisfied) totalSuccesses++
        totalEvaluated++
    }

    protected fun recordIteration() {
        if (config.debugMode) {
            latestSequenceSentence = ArrayList()
            latestSequenceLiterals = ArrayList()
        }
        totalIterations++
    }

    protected fun recordFlip(sentence: Sentence, literal: Int) {
        if (config.debugMode) {
            latestSequenceSentence!!.add(sentence)
            latestSequenceLiterals!!.add(literal)
        }
        totalFlips++
    }

}

class LocalSearchOptimizer<O : ObjectiveFunction>(problem: Problem,
                                                  override val config: SolverConfig = SolverConfig(),
                                                  propTable: UnitPropagationTable? = UnitPropagationTable(problem),
                                                  timeout: Long = -1L,
                                                  maxRestarts: Int = 10,
                                                  maxSteps: Int = problem.nbrVariables,
                                                  maxSidewaySteps: Int = max(10, maxSteps / 4),
                                                  pRandomWalk: Double = 0.05,
                                                  maxConsideration: Int = 32) :
        LocalSearch(problem, propTable, timeout, maxRestarts, maxSteps, maxSidewaySteps, pRandomWalk, maxConsideration), Optimizer<O> {

    override fun optimizeOrThrow(function: O, contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        var bestLabeling: Labeling? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in 1..maxRestarts) {
            recordIteration()
            val labeling = optIteration(contextLiterals, end)
            if (labeling != null) {
                val score = let {
                    val d = function.value(labeling)
                    if (config.maximize) d else -d
                }
                if (score > bestScore) {
                    bestLabeling = labeling
                    bestScore = score
                }
            }
            if (millis() > end) break
        }
        recordCompleted(bestLabeling != null)
        return bestLabeling ?: if (millis() > end) throw TimeoutException(timeout)
        else throw IterationsReachedException(maxRestarts)
    }

    private fun optIteration(contextLiterals: Literals, end: Long): Labeling? {
        // recordFlip
        TODO()
    }
}

class LocalSearchSolver(problem: Problem,
                        override val config: SolverConfig = SolverConfig(),
                        propTable: UnitPropagationTable? = UnitPropagationTable(problem),
                        timeout: Long = -1L,
                        maxRestarts: Int = 10,
                        maxSteps: Int = problem.nbrVariables,
                        maxSidewaySteps: Int = max(10, maxSteps / 4),
                        pRandomWalk: Double = 0.05,
                        maxConsideration: Int = 32) :
        LocalSearch(problem, propTable, timeout, maxRestarts, maxSteps, maxSidewaySteps, pRandomWalk, maxConsideration), Solver {


    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        for (i in 1..maxRestarts) {
            recordIteration()
            val labeling = satIteration(contextLiterals, end)
            if (labeling != null) {
                recordCompleted(true)
                return labeling
            }
            if (millis() > end) throw TimeoutException(timeout)
        }
        recordCompleted(false)
        throw IterationsReachedException(maxRestarts)
    }


    private fun satIteration(contextLiterals: Literals, end: Long): MutableLabeling? {
        val rng = config.nextRandom()

        val tracker = if (propTable != null)
            PropLabelingTracker(config.labelingBuilder.build(problem.nbrVariables), problem, propTable, contextLiterals, rng)
        else
            FlipLabelingTracker(config.labelingBuilder.generate(problem.nbrVariables, rng), problem, contextLiterals)

        val improvement = IntArray(
                if (maxConsideration <= 0) problem.sentences.maxBy { it.literals.size }?.size ?: 0
                else maxConsideration)

        val contextIxs = IntSet().apply {
            contextLiterals.forEach { i ->
                this.add(i.asIx())
                val sentences = if (propTable != null) propTable.literalPropagations[i]
                else problem.sentencesWith(i.asIx())
                sentences.forEach { j -> this.add(j.asIx()) }
            }
        }

        for (flips in 1..maxSteps) {
            if (tracker.unsatisfied.isEmpty())
                return tracker.labeling

            // Pick random unsatisfied clause
            val pickedSentenceIx = tracker.unsatisfied.random(rng)
            val pickedSentence = problem.sentences[pickedSentenceIx]
            val literals = pickedSentence.literals

            val ix = if (pRandomWalk > rng.nextDouble()) literals[rng.nextInt(literals.size)].asIx()
            else chooseBest(tracker, contextIxs, literals, improvement, rng)

            if (ix < 0 || ix in contextIxs) continue
            tracker.set(!tracker.labeling.asLiteral(ix))
            tracker.updateUnsatisfied(tracker.labeling.asLiteral(ix))

            recordFlip(pickedSentence, tracker.labeling.asLiteral(ix))
            if (millis() > end) return null
        }
        return null
    }

    private fun chooseBest(tracker: LabelingTracker,
                           contextIds: IntSet,
                           literals: Literals,
                           improvement: IntArray,
                           rng: Random): Int {
        val perm = if (literals.size >= improvement.size) IntPermutation(literals.size, rng) else null
        for (i in 0 until min(improvement.size, literals.size)) {
            improvement[i] = 0
            val ix = literals[perm?.encode(i) ?: i].asIx()
            val sents = if (propTable != null) propTable.literalSentences[ix]
            else problem.sentencesWith(ix)
            if (ix in contextIds) {
                improvement[i] = -1
                continue
            }
            for (sentenceIx in sents) {
                val sent = problem.sentences[sentenceIx]
                improvement[i] += if (sentenceIx in tracker.unsatisfied) sent.flipsToSatisfy(tracker.labeling) else 0
            }
            val lit = tracker.labeling.asLiteral(ix)
            tracker.set(!lit)
            for (sentenceIx in sents) {
                val sent = problem.sentences[sentenceIx]
                improvement[i] -= sent.flipsToSatisfy(tracker.labeling)
            }
            tracker.undo(!lit)
        }
        val imprIx = IntPermutation(min(literals.size, improvement.size), rng).let {
            it.maxBy { i ->
                improvement[i]
            }!!
        }
        val ix = literals[perm?.encode(imprIx) ?: imprIx].asIx()
        return if (improvement[imprIx] < 0) -1
        else ix
    }
}

