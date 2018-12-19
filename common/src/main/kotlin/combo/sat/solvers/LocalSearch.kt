package combo.sat.solvers

import combo.math.IntPermutation
import combo.sat.*
import combo.util.IntSet
import combo.util.millis
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

sealed class LocalSearch(val problem: Problem, val propTable: UnitPropagationTable?) {
    abstract val config: SolverConfig

    var totalSuccesses: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set
    var totalIterations: Long = 0
        private set

    protected fun recordCompleted(satisfied: Boolean) {
        if (satisfied) totalSuccesses++
        totalEvaluated++
    }

    protected fun recordIteration() {
        totalIterations++
    }

    protected fun pickSatLit(tracker: LabelingTracker, literals: Literals,
                             pRandomWalk: Double, rng: Random, assumptionIxs: IntSet, maxConsideration: Int): Int {
        val litIx = if (pRandomWalk > rng.nextDouble()) {
            IntPermutation(literals.size).firstOrNull {
                literals[it].asIx() !in assumptionIxs
            } ?: -1
        } else {
            val n = min(maxConsideration, literals.size)
            val perm = if (literals.size > maxConsideration) IntPermutation(literals.size) else null
            var maxImp = -1
            var bestLitIx = -1
            for (k in 0 until n) {
                val i = perm?.encode(k) ?: k
                val ix = literals[i].asIx()
                if (ix in assumptionIxs) continue
                val sents = if (propTable != null) propTable.variableSentences[ix]
                else problem.sentencesWith(ix)
                val pre = sents.sumBy {
                    if (it in tracker.unsatisfied) problem.sentences[it].flipsToSatisfy(tracker.labeling) else 0
                }
                val lit = !tracker.labeling.asLiteral(ix)
                tracker.set(lit)
                val post = sents.sumBy { problem.sentences[it].flipsToSatisfy(tracker.labeling) }
                tracker.undo(lit)
                val score = pre - post
                if (score > maxImp) {
                    bestLitIx = i
                    maxImp = score
                }
            }
            bestLitIx
        }
        if (litIx < 0) return -1
        return literals[litIx].asIx()
    }

    protected fun makeContextSet(assumptions: Literals) = IntSet().apply {
        assumptions.forEach { lit ->
            add(lit.asIx())
            if (propTable != null) {
                val sentences = propTable.literalPropagations[lit]
                sentences.forEach { j -> add(j.asIx()) }
            }
        }
    }

}

class LocalSearchOptimizer<O : ObjectiveFunction>(problem: Problem,
                                                  override val config: SolverConfig = SolverConfig(),
                                                  propTable: UnitPropagationTable? = UnitPropagationTable(problem),
                                                  val timeout: Long = -1L,
                                                  val restarts: Int = 5,
                                                  val maxSteps: Int = problem.nbrVariables,
                                                  val maxSidewaySteps: Int = problem.nbrVariables / 2,
                                                  val pRandomWalk: Double = 0.05,
                                                  val greedyHeuristic: Boolean = true,
                                                  val maxConsideration: Int = 32) : LocalSearch(problem, propTable), Optimizer<O> {

    private fun penalty(labeling: Labeling) = problem.flipsToSatisfy(labeling)

    protected fun pickSatOpt(tracker: LabelingTracker, function: ObjectiveFunction, lowerBound: Double, upperBound: Double,
                             pRandomWalk: Double, rng: Random, assumptionIxs: IntSet, maxConsideration: Int): Int {
        if (pRandomWalk > rng.nextDouble()) {
            return IntPermutation(problem.nbrVariables).firstOrNull {
                it !in assumptionIxs
            } ?: -1
        } else {
            val n = min(maxConsideration, problem.nbrVariables)
            val perm = if (problem.nbrVariables > maxConsideration) IntPermutation(problem.nbrVariables) else null
            var maxValue = Double.NEGATIVE_INFINITY
            var bestIx = -1
            for (k in 0 until n) {
                val ix = perm?.encode(k) ?: k
                if (ix in assumptionIxs) continue
                val lit = !tracker.labeling.asLiteral(ix)
                tracker.set(lit)
                val value = function.value(tracker.labeling, penalty(tracker.labeling), lowerBound, upperBound, config.maximize).also {
                    tracker.undo(lit)
                }
                if (value > maxValue) {
                    maxValue = value
                    bestIx = ix
                }
            }
            return bestIx
        }
    }

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val assumptionIxs = makeContextSet(assumptions)

        var bestScore = Double.NEGATIVE_INFINITY
        var bestLabeling: Labeling? = null
        val lowerBound = function.lowerBound(config.maximize)
        val upperBound = function.upperBound(config.maximize)
        var sidewaySteps = 0
        val _maxCon = max(2, min(maxConsideration, problem.nbrVariables))

        for (restart in 1..restarts) {
            recordIteration()
            val rng = config.nextRandom()
            val selector = if (greedyHeuristic && function is LinearObjective) WeightSelector(function.weights, rng) else RandomSelector(rng)

            val tracker = if (propTable != null)
                PropLabelingTracker(config.labelingBuilder.build(problem.nbrVariables), problem, propTable, assumptions, rng, selector)
            else
                FlipLabelingTracker(config.labelingBuilder.build(problem.nbrVariables), problem, assumptions, selector)

            fun update(score: Double) {
                if (score > bestScore && tracker.unsatisfied.isEmpty()) {
                    bestScore = score
                    bestLabeling = tracker.labeling.copy()
                }
            }

            var prevScore = function.value(tracker.labeling, penalty(tracker.labeling), lowerBound, upperBound, config.maximize)
            update(prevScore)

            for (flips in 1..maxSteps) {
                val ix = if (tracker.unsatisfied.isNotEmpty()) {
                    val literals = problem.sentences[tracker.unsatisfied.random(rng)].literals
                    val ix = pickSatLit(tracker, literals, pRandomWalk, rng, assumptionIxs, _maxCon)
                    if (ix < 0 || ix in assumptionIxs) continue
                    ix
                } else {
                    val ix = pickSatOpt(tracker, function, lowerBound, upperBound, pRandomWalk, rng, assumptionIxs, _maxCon)
                    if (ix < 0 || ix in assumptionIxs) break
                    ix
                }

                if (ix in assumptionIxs) {
                    throw IllegalArgumentException()
                }
                val lit = !tracker.labeling.asLiteral(ix)
                tracker.set(lit)
                tracker.updateUnsatisfied(tracker.labeling.asLiteral(ix))
                val score = function.value(tracker.labeling, penalty(tracker.labeling), lowerBound, upperBound, config.maximize)
                if (score == upperBound) {
                    recordCompleted(true)
                    return tracker.labeling
                } else if (score < prevScore) {
                    tracker.set(!lit)
                    tracker.updateUnsatisfied(!lit)
                    break
                } else if (score == prevScore && sidewaySteps++ >= maxSidewaySteps)
                    break
                update(score)
                prevScore = score
                if (millis() > end) break
            }
            if (millis() > end) break
        }
        recordCompleted(bestLabeling != null)
        return bestLabeling
                ?: (if (millis() > end) throw TimeoutException(timeout) else throw IterationsReachedException(restarts))
    }
}

class LocalSearchSolver(problem: Problem,
                        override val config: SolverConfig = SolverConfig(),
                        propTable: UnitPropagationTable? = UnitPropagationTable(problem),
                        val timeout: Long = -1L,
                        val maxRestarts: Int = 10,
                        val maxSteps: Int = problem.nbrVariables,
                        val pRandomWalk: Double = 0.05,
                        val maxConsideration: Int = 20) : LocalSearch(problem, propTable), Solver {

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val assumptionIxs = makeContextSet(assumptions)
        val _maxCon = max(2, min(maxConsideration, problem.nbrVariables))

        for (restart in 1..maxRestarts) {
            recordIteration()
            val rng = config.nextRandom()

            val tracker = if (propTable != null)
                PropLabelingTracker(config.labelingBuilder.build(problem.nbrVariables), problem, propTable, assumptions, rng, RandomSelector(rng))
            else
                FlipLabelingTracker(config.labelingBuilder.generate(problem.nbrVariables, rng), problem, assumptions, RandomSelector(rng))

            for (flips in 1..maxSteps) {
                if (tracker.unsatisfied.isEmpty()) {
                    recordCompleted(true)
                    return tracker.labeling
                }

                // Pick random unsatisfied clause
                val literals = problem.sentences[tracker.unsatisfied.random(rng)].literals
                val ix = pickSatLit(tracker, literals, pRandomWalk, rng, assumptionIxs, _maxCon)
                if (ix < 0) continue
                tracker.set(!tracker.labeling.asLiteral(ix))
                tracker.updateUnsatisfied(tracker.labeling.asLiteral(ix))
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

