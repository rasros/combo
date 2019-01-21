package combo.sat.solvers

import combo.math.IntPermutation
import combo.math.RandomSequence
import combo.sat.*
import combo.util.millis
import combo.util.nanos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * TODO explain parameters
 */
open class LocalSearchOptimizer<in O : ObjectiveFunction>(val problem: Problem,
                                                          val timeout: Long = -1L,
                                                          val randomSeed: Long = nanos(),
                                                          val restarts: Int = 5,
                                                          val maxSteps: Int = max(1, problem.nbrVariables),
                                                          val pRandomWalk: Double = 0.0,
                                                          val labelingFactory: LabelingFactory = BitFieldLabelingFactory,
                                                          val stateFactory: SearchStateFactory = PropSearchStateFactory(problem),
                                                          val selector: ValueSelector<O> = RandomSelector,
                                                          val eps: Double = 1E-4,
                                                          val maxConsideration: Int = max(20, problem.nbrVariables / 5))
    : Optimizer<O> {

    private val randomSequence = RandomSequence(randomSeed)

    var totalSuccesses: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set
    var totalIterations: Long = 0
        private set

    private fun recordCompleted(satisfied: Boolean) {
        if (satisfied) totalSuccesses++
        totalEvaluated++
    }

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE

        val adjustedMaxConsideration = max(2, min(maxConsideration, problem.nbrVariables))

        var bestValue = Double.POSITIVE_INFINITY
        var bestLabeling: Labeling? = null

        val lowerBound = function.lowerBound()

        for (restart in 1..restarts) {
            totalIterations++
            val rng = randomSequence.next()

            val labeling = labelingFactory.create(problem.nbrVariables)
            val state = stateFactory.build(labeling, assumptions, selector, function, rng)

            fun setReturnValue(value: Double) {
                if (value < bestValue && state.totalUnsatisfied == 0) {
                    bestValue = value
                    bestLabeling = state.labeling.copy()
                }
            }

            var prevValue = function.value(labeling)
            setReturnValue(prevValue)

            if (state.totalUnsatisfied == 0 && (abs(bestValue - lowerBound) < eps || problem.nbrVariables == 0)) {
                recordCompleted(true)
                return state.labeling
            }

            for (flips in 1..maxSteps) {
                val n: Int
                val ix: Int = if (pRandomWalk > rng.nextDouble()) {
                    if (state.totalUnsatisfied > 0) state.randomUnsatisfied(rng).literals.random(rng).toIx()
                    else rng.nextInt(problem.nbrVariables)
                } else {
                    val itr: IntIterator = if (state.totalUnsatisfied > 0) {
                        val literals = state.randomUnsatisfied(rng).literals
                        n = min(adjustedMaxConsideration, literals.size)
                        literals.permutation(rng)
                    } else {
                        n = min(adjustedMaxConsideration, problem.nbrVariables)
                        if (problem.nbrVariables > adjustedMaxConsideration) IntPermutation(problem.nbrVariables).iterator()
                        else (0 until problem.nbrVariables).iterator()
                    }
                    var maxSatImp = Int.MIN_VALUE
                    var maxOptImp = 0.0
                    var bestIx = -1
                    for (k in 0 until n) {
                        val ix = itr.nextInt().toIx()
                        val satScore = state.improvement(ix)
                        val optScore = function.improvement(labeling, ix, state.changes(ix))
                        if (satScore > maxSatImp || (satScore == maxSatImp && optScore > maxOptImp)) {
                            bestIx = ix
                            maxSatImp = satScore
                            maxOptImp = optScore
                        }
                    }
                    bestIx
                }

                if (ix < 0) break
                val improvement = function.improvement(labeling, ix, state.changes(ix))
                val score = prevValue - improvement
                state.flip(ix)
                setReturnValue(score)

                if (flips.rem(1) == 0) prevValue = function.value(labeling)
                else prevValue -= improvement
                if (state.totalUnsatisfied == 0) {
                    if (abs(bestValue - lowerBound) < eps) {
                        recordCompleted(true)
                        return state.labeling
                    } else if (improvement < eps) break
                }
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
                        timeout: Long = -1L,
                        randomSeed: Long = nanos(),
                        restarts: Int = Int.MAX_VALUE,
                        maxSteps: Int = max(1, problem.nbrVariables),
                        pRandomWalk: Double = 0.05,
                        labelingFactory: LabelingFactory = BitFieldLabelingFactory,
                        stateFactory: SearchStateFactory = PropSearchStateFactory(problem),
                        selector: ValueSelector<SatObjective> = RandomSelector,
                        eps: Double = 1E-4,
                        maxConsideration: Int = max(20, problem.nbrVariables / 5)) : LocalSearchOptimizer<SatObjective>(
        problem, timeout, randomSeed, restarts, maxSteps, pRandomWalk, labelingFactory, stateFactory, selector, eps, maxConsideration), Solver {

    override fun witnessOrThrow(assumptions: Literals) = optimizeOrThrow(SatObjective, assumptions)
}

