package combo.sat.solvers

import combo.math.IntPermutation
import combo.sat.*
import combo.util.IntSet
import combo.util.millis
import kotlin.math.max
import kotlin.math.min

sealed class LocalSearch(val problem: Problem,
                         val propTable: UnitPropagationTable?,
                         val timeout: Long,
                         val maxRestarts: Int,
                         val maxSteps: Int,
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
                                                  pRandomWalk: Double = 0.05,
                                                  maxConsideration: Int = 32) :
        LocalSearch(problem, propTable, timeout, maxRestarts, maxSteps, pRandomWalk, maxConsideration), Optimizer<O> {

    override fun optimizeOrThrow(function: O, contextLiterals: Literals): Labeling {
        TODO()
    }
}

class LocalSearchSolver(problem: Problem,
                        override val config: SolverConfig = SolverConfig(),
                        propTable: UnitPropagationTable? = UnitPropagationTable(problem),
                        timeout: Long = -1L,
                        maxRestarts: Int = 10,
                        maxSteps: Int = problem.nbrVariables,
                        pRandomWalk: Double = 0.05,
                        maxConsideration: Int = 32) :
        LocalSearch(problem, propTable, timeout, maxRestarts, maxSteps, pRandomWalk, maxConsideration), Solver {


    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val contextIxs = IntSet().apply {
            contextLiterals.forEach { i ->
                this.add(i.asIx())
                val sentences = if (propTable != null) propTable.literalPropagations[i]
                else problem.sentencesWith(i.asIx())
                sentences.forEach { j -> this.add(j.asIx()) }
            }
        }
        val _maxCon = max(2, min(maxConsideration, problem.nbrVariables))

        for (restart in 1..maxRestarts) {
            recordIteration()
            val rng = config.nextRandom()

            val tracker = if (propTable != null)
                PropLabelingTracker(config.labelingBuilder.build(problem.nbrVariables), problem, propTable, contextLiterals, rng)
            else
                FlipLabelingTracker(config.labelingBuilder.generate(problem.nbrVariables, rng), problem, contextLiterals)

            for (flips in 1..maxSteps) {
                if (tracker.unsatisfied.isEmpty()) {
                    recordCompleted(true)
                    return tracker.labeling
                }

                // Pick random unsatisfied clause
                val pickedSentenceIx = tracker.unsatisfied.random(rng)
                val pickedSentence = problem.sentences[pickedSentenceIx]
                val literals = pickedSentence.literals

                val litIx = if (pRandomWalk > rng.nextDouble()) {
                    IntPermutation(literals.size).firstOrNull {
                        literals[it].asIx() !in contextIxs
                    } ?: -1
                } else {
                    val litIxs = if (literals.size >= _maxCon) IntPermutation(literals.size, rng) else (0 until literals.size)
                    litIxs.maxBy { i ->
                        val ix = literals[i].asIx()
                        if (ix in contextIxs)
                            -1
                        else {
                            val sents = if (propTable != null) propTable.variableSentences[ix]
                            else problem.sentencesWith(ix)
                            val pre = sents.sumBy { if (it in tracker.unsatisfied) problem.sentences[it].flipsToSatisfy(tracker.labeling) else 0 }
                            val lit = !tracker.labeling.asLiteral(ix)
                            tracker.set(lit)
                            val post = sents.sumBy { problem.sentences[it].flipsToSatisfy(tracker.labeling) }
                            tracker.undo(lit)
                            pre - post
                        }
                    } ?: -1
                }
                if (litIx < 0) continue
                val ix = literals[litIx].asIx()
                if (ix in contextIxs) continue
                tracker.set(!tracker.labeling.asLiteral(ix))
                tracker.updateUnsatisfied(tracker.labeling.asLiteral(ix))

                recordFlip(pickedSentence, tracker.labeling.asLiteral(ix))
                if (millis() > end) {
                    recordCompleted(false)
                    throw TimeoutException(timeout)
                }
            }
        }

        recordCompleted(false)
        throw IterationsReachedException(maxRestarts)
    }

}

